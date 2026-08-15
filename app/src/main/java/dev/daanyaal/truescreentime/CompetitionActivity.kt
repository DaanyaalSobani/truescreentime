package dev.daanyaal.truescreentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The screen-time contest: create or join a group, then track your own
 * score under the group's shared rules. Sharing other members' numbers
 * comes next; this screen already computes the number that will be shared.
 */
class CompetitionActivity : AppCompatActivity() {

    private lateinit var history: UsageHistory
    private lateinit var filterStore: FilterStore
    private lateinit var groupStore: GroupStore

    private lateinit var emptyState: View
    private lateinit var groupState: View
    private lateinit var groupNameView: TextView
    private lateinit var periodView: TextView
    private lateinit var progressView: TextView
    private lateinit var totalView: TextView
    private lateinit var averageView: TextView
    private lateinit var rulesView: TextView
    private lateinit var chart: WeeklyBarChartView
    private lateinit var dayDetailView: TextView

    private var loadJob: Job? = null
    private var dayStarts: List<Long> = emptyList()
    private var dailyTotals: LongArray = LongArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_competition)

        history = UsageHistory(this)
        filterStore = FilterStore(this)
        groupStore = GroupStore(this)

        emptyState = findViewById(R.id.competition_empty)
        groupState = findViewById(R.id.competition_group)
        groupNameView = findViewById(R.id.group_name)
        periodView = findViewById(R.id.group_period)
        progressView = findViewById(R.id.group_progress)
        totalView = findViewById(R.id.group_total)
        averageView = findViewById(R.id.group_average)
        rulesView = findViewById(R.id.group_rules)
        chart = findViewById(R.id.competition_chart)
        dayDetailView = findViewById(R.id.competition_day_detail)

        findViewById<Button>(R.id.create_group).setOnClickListener { showCreateDialog() }
        findViewById<Button>(R.id.join_group).setOnClickListener { showJoinDialog() }
        findViewById<Button>(R.id.share_code).setOnClickListener { shareInvite() }
        findViewById<Button>(R.id.leave_group).setOnClickListener { confirmLeave() }

        chart.onDaySelected = { index -> showDay(index) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val rules = groupStore.activeGroup
        emptyState.visibility = if (rules == null) View.VISIBLE else View.GONE
        groupState.visibility = if (rules == null) View.GONE else View.VISIBLE
        if (rules != null) loadScores(rules)
    }

    private fun loadScores(rules: GroupRules) {
        val todayStart = DateRange.startOfToday()
        groupNameView.text = rules.name
        periodView.text = getString(
            R.string.range_between,
            formatDay(rules.periodStartDay),
            formatDay(rules.lastDayStart),
        )
        progressView.text = when {
            !CompetitionPeriod.hasStarted(rules, todayStart) ->
                getString(R.string.competition_not_started)
            CompetitionPeriod.isComplete(rules, todayStart) ->
                getString(R.string.competition_finished)
            else -> getString(
                R.string.competition_day_of,
                CompetitionPeriod.daysElapsed(rules, todayStart),
                rules.periodDays,
                CompetitionPeriod.daysRemaining(rules, todayStart),
            )
        }
        rulesView.text = getString(
            R.string.competition_rules_summary,
            rules.excludedPackages.size,
            if (rules.countSystemApps) {
                getString(R.string.competition_system_counted)
            } else {
                getString(R.string.competition_system_ignored)
            },
        )
        dayDetailView.text = ""

        val days = CompetitionPeriod.dayStarts(rules)
        dayStarts = days

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val totals = withContext(Dispatchers.IO) {
                LongArray(days.size) { index ->
                    val dayStart = days[index]
                    // Days that have not happened yet score nothing.
                    if (dayStart > todayStart) {
                        0L
                    } else {
                        CompetitionScore.dailyTotalMs(
                            history.dayUsage(dayStart, todayStart), rules
                        )
                    }
                }
            }
            dailyTotals = totals

            val elapsed = CompetitionPeriod.daysElapsed(rules, todayStart)
            val scored = totals.take(elapsed)
            totalView.text = TimeFormat.format(
                this@CompetitionActivity, CompetitionScore.totalMs(scored)
            )
            averageView.text = getString(
                R.string.competition_daily_average,
                TimeFormat.format(
                    this@CompetitionActivity, CompetitionScore.dailyAverageMs(scored)
                ),
            )

            val labels = days.map {
                SimpleDateFormat("d", Locale.getDefault()).format(Date(it))
            }
            val selected = (elapsed - 1).coerceAtLeast(0)
            chart.setData(totals, labels, selected)
        }
    }

    private fun showDay(index: Int) {
        val dayStart = dayStarts.getOrNull(index) ?: return
        val total = dailyTotals.getOrNull(index) ?: return
        dayDetailView.text = getString(
            R.string.day_detail,
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(dayStart)),
            TimeFormat.format(this, total),
        )
    }

    private fun showCreateDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val nameField = view.findViewById<EditText>(R.id.field_group_name)
        val daysField = view.findViewById<EditText>(R.id.field_period_days)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_group)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = nameField.text.toString().ifBlank {
                    getString(R.string.default_group_name)
                }
                val days = daysField.text.toString().toIntOrNull()?.coerceIn(1, 366) ?: 14
                createGroup(name, days)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * The creator's current exclusions become the group's agreed rules —
     * everyone who joins scores against these, not their own.
     */
    private fun createGroup(name: String, periodDays: Int) {
        groupStore.activeGroup = GroupRules(
            groupId = GroupStore.randomHex(4),
            name = name,
            periodStartDay = DateRange.startOfToday(),
            periodDays = periodDays,
            timeZoneId = TimeZone.getDefault().id,
            excludedPackages = filterStore.excludedPackages(),
            countSystemApps = filterStore.showSystemApps,
            sharingLevel = SharingLevel.TOP_APPS,
            secretHex = GroupStore.randomHex(16),
        )
        render()
    }

    private fun showJoinDialog() {
        val field = EditText(this).apply {
            setHint(R.string.join_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.join_group)
            .setView(field)
            .setPositiveButton(R.string.join) { _, _ ->
                val rules = GroupCodec.decode(field.text.toString())
                if (rules == null) {
                    Toast.makeText(this, R.string.join_invalid, Toast.LENGTH_LONG).show()
                } else {
                    groupStore.activeGroup = rules
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareInvite() {
        val rules = groupStore.activeGroup ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.invite_message, rules.name, GroupCodec.encode(rules)),
            )
        startActivity(Intent.createChooser(intent, getString(R.string.share_code)))
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle(R.string.leave_group)
            .setMessage(R.string.leave_group_confirm)
            .setPositiveButton(R.string.leave_group) { _, _ ->
                groupStore.leave()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDay(dayStart: Long): String =
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dayStart))

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, CompetitionActivity::class.java))
        }
    }
}
