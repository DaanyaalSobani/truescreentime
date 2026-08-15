package dev.daanyaal.truescreentime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
 * The screen-time contest: create or join a group, track your score under
 * the group's shared rules, and exchange signed scorecards with friends.
 */
class CompetitionActivity : AppCompatActivity() {

    private lateinit var history: UsageHistory
    private lateinit var filterStore: FilterStore
    private lateinit var groupStore: GroupStore
    private lateinit var scoreStore: ScoreStore

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
    private lateinit var leaderboardView: LinearLayout
    private lateinit var leaderboardEmptyView: TextView

    private var loadJob: Job? = null
    private var dayStarts: List<Long> = emptyList()
    private var dailyTotals = LongArray(0)

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importFromImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_competition)

        history = UsageHistory(this)
        filterStore = FilterStore(this)
        groupStore = GroupStore(this)
        scoreStore = ScoreStore(this)

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
        leaderboardView = findViewById(R.id.leaderboard)
        leaderboardEmptyView = findViewById(R.id.leaderboard_empty)

        findViewById<Button>(R.id.create_group).setOnClickListener { showCreateDialog() }
        findViewById<Button>(R.id.join_group).setOnClickListener { showJoinDialog() }
        findViewById<Button>(R.id.share_code).setOnClickListener { shareInvite() }
        findViewById<Button>(R.id.leave_group).setOnClickListener { confirmLeave() }
        findViewById<Button>(R.id.share_scorecard).setOnClickListener { shareScorecard() }
        findViewById<Button>(R.id.import_scorecard).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<Button>(R.id.paste_scorecard).setOnClickListener { showPasteDialog() }

        chart.onDaySelected = { index -> showDay(index) }

        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /** Something was shared into the app — an image or a pasted code. */
    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type ?: return
        when {
            type.startsWith("image/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) importFromImage(uri)
            }
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) importPayload(text)
            }
        }
    }

    private fun render() {
        val rules = groupStore.activeGroup
        emptyState.visibility = if (rules == null) View.VISIBLE else View.GONE
        groupState.visibility = if (rules == null) View.GONE else View.VISIBLE
        if (rules != null) {
            loadScores(rules)
            renderLeaderboard(rules)
        }
    }

    private fun renderLeaderboard(rules: GroupRules) {
        val blocks = scoreStore.blocks(rules.groupId).values
        leaderboardView.removeAllViews()
        leaderboardEmptyView.visibility = if (blocks.isEmpty()) View.VISIBLE else View.GONE

        ScorecardMerge.leaderboard(blocks).forEachIndexed { index, block ->
            val row = layoutInflater.inflate(R.layout.item_leaderboard, leaderboardView, false)
            row.findViewById<TextView>(R.id.rank).text = getString(R.string.rank, index + 1)
            row.findViewById<TextView>(R.id.member_name).text = buildString {
                append(block.displayName.ifBlank { getString(R.string.unnamed_member) })
                if (block.memberId == groupStore.memberId) {
                    append(getString(R.string.card_you_suffix))
                }
            }
            row.findViewById<TextView>(R.id.member_total).text =
                TimeFormat.format(this, block.totalMs)
            row.findViewById<TextView>(R.id.member_detail).text = getString(
                R.string.leaderboard_detail,
                block.reportedDays,
                if (block.isFirstHand) {
                    getString(R.string.leaderboard_verified)
                } else {
                    getString(R.string.leaderboard_relayed)
                },
            )
            leaderboardView.addView(row)
        }
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

            val labels = days.map { SimpleDateFormat("d", Locale.getDefault()).format(Date(it)) }
            chart.setData(totals, labels, (elapsed - 1).coerceAtLeast(0))
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

    // ---- sharing -----------------------------------------------------------

    /** Signs this device's block, merges it in, and shares everything known. */
    private fun shareScorecard() {
        val rules = groupStore.activeGroup ?: return
        if (groupStore.displayName.isBlank()) {
            askForName { shareScorecard() }
            return
        }
        val todayStart = DateRange.startOfToday()
        val elapsed = CompetitionPeriod.daysElapsed(rules, todayStart)

        lifecycleScope.launch {
            val payloadAndImage = withContext(Dispatchers.IO) {
                val minutes = dayStarts.indices.map { index ->
                    if (index < elapsed) {
                        (dailyTotals.getOrElse(index) { 0L } / 60_000L).toInt()
                    } else {
                        MemberBlock.UNKNOWN
                    }
                }
                val keys = scoreStore.keyPair()
                val revision = scoreStore.revision + 1
                val unsigned = MemberBlock(
                    memberId = groupStore.memberId,
                    displayName = groupStore.displayName,
                    revision = revision,
                    dailyMinutes = minutes,
                )
                val mine = unsigned.copy(
                    publicKeyHex = ScorecardCrypto.encodePublicKey(keys.public),
                    signatureHex = ScorecardCrypto.sign(
                        unsigned.signingPayload(rules.groupId), keys.private
                    ),
                )
                scoreStore.revision = revision

                val blocks = scoreStore.blocks(rules.groupId).toMutableMap()
                blocks[mine.memberId] = mine
                scoreStore.saveBlocks(rules, blocks.values)

                val payload = ScorecardCodec.encode(
                    Scorecard(
                        rules.groupId, rules.periodStartDay, rules.periodDays, blocks.values.toList()
                    )
                )
                val bitmap = ScorecardImage.render(
                    this@CompetitionActivity, rules, blocks.values, payload, mine.memberId
                )
                payload to ScorecardSharing.writeShareableImage(this@CompetitionActivity, bitmap)
            }

            renderLeaderboard(rules)
            val (payload, uri) = payloadAndImage
            val intent = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, payload)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(
                Intent.createChooser(intent, getString(R.string.share_scorecard))
            )
        }
    }

    private fun importFromImage(uri: Uri) {
        lifecycleScope.launch {
            val payload = withContext(Dispatchers.IO) {
                ScorecardSharing.readQrFromImage(this@CompetitionActivity, uri)
            }
            if (payload == null) {
                Toast.makeText(
                    this@CompetitionActivity, R.string.import_no_code, Toast.LENGTH_LONG
                ).show()
            } else {
                importPayload(payload)
            }
        }
    }

    /** Accepts a scorecard, or an invite code if we are not in a group yet. */
    private fun importPayload(text: String) {
        val trimmed = text.trim()
        val rules = groupStore.activeGroup

        val invite = GroupCodec.decode(trimmed)
        if (invite != null) {
            if (rules == null) {
                groupStore.activeGroup = invite
                render()
                Toast.makeText(this, R.string.joined_group, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.already_in_group, Toast.LENGTH_LONG).show()
            }
            return
        }

        val card = ScorecardCodec.decode(trimmed)
        if (card == null) {
            Toast.makeText(this, R.string.import_unreadable, Toast.LENGTH_LONG).show()
            return
        }
        if (rules == null) {
            Toast.makeText(this, R.string.import_no_group, Toast.LENGTH_LONG).show()
            return
        }
        if (card.groupId != rules.groupId) {
            Toast.makeText(this, R.string.import_other_group, Toast.LENGTH_LONG).show()
            return
        }

        val known = scoreStore.blocks(rules.groupId)
        val result = ScorecardMerge.merge(known, card, rules.groupId) { block ->
            verifyBlock(block, known, rules)
        }
        scoreStore.saveBlocks(rules, result.blocks.values)
        renderLeaderboard(rules)

        Toast.makeText(
            this,
            if (result.changed) {
                getString(R.string.import_merged, result.newMembers, result.updatedMembers)
            } else {
                getString(R.string.import_nothing_new)
            },
            Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * A signature must check out, and once a member's key is known it must
     * keep matching — otherwise anyone could mint a new key and impersonate
     * them.
     */
    private fun verifyBlock(
        block: MemberBlock,
        known: Map<String, MemberBlock>,
        rules: GroupRules,
    ): Boolean {
        val pinned = known[block.memberId]?.publicKeyHex
        if (!pinned.isNullOrEmpty() && pinned != block.publicKeyHex) return false
        return ScorecardCrypto.verify(
            block.signingPayload(rules.groupId), block.signatureHex, block.publicKeyHex
        )
    }

    // ---- dialogs -----------------------------------------------------------

    private fun askForName(onNamed: () -> Unit) {
        val field = EditText(this).apply {
            setHint(R.string.your_name_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.your_name)
            .setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                groupStore.displayName = field.text.toString().trim()
                if (groupStore.displayName.isNotBlank()) onNamed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun showJoinDialog() = promptForText(R.string.join_group, R.string.join_hint)

    private fun showPasteDialog() =
        promptForText(R.string.paste_scorecard, R.string.paste_scorecard_hint)

    private fun promptForText(titleRes: Int, hintRes: Int) {
        val field = EditText(this).apply {
            setHint(hintRes)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importPayload(field.text.toString())
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
        val rules = groupStore.activeGroup ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.leave_group)
            .setMessage(R.string.leave_group_confirm)
            .setPositiveButton(R.string.leave_group) { _, _ ->
                scoreStore.clear(rules.groupId)
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
