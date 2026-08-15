package dev.daanyaal.truescreentime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-app weekly breakdown: seven daily bars for one package, with
 * chevron buttons stepping one week back/forward.
 */
class AppDetailActivity : AppCompatActivity() {

    private lateinit var history: UsageHistory
    private lateinit var weekTotalView: TextView
    private lateinit var weekLabelView: TextView
    private lateinit var dayDetailView: TextView
    private lateinit var weekChart: WeeklyBarChartView
    private lateinit var prevWeekButton: ImageButton
    private lateinit var nextWeekButton: ImageButton

    private lateinit var targetPackage: String
    private var weeksBack = 0
    private var weekDayStarts = LongArray(7)
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        history = UsageHistory(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL) ?: targetPackage

        findViewById<TextView>(R.id.detail_app_name).text = label
        val iconView = findViewById<ImageView>(R.id.detail_app_icon)
        val icon = try {
            packageManager.getApplicationIcon(targetPackage)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (icon != null) iconView.setImageDrawable(icon)
        else iconView.setImageResource(R.drawable.ic_default_app)

        weekTotalView = findViewById(R.id.detail_week_total)
        weekLabelView = findViewById(R.id.detail_week_label)
        dayDetailView = findViewById(R.id.detail_day_detail)
        weekChart = findViewById(R.id.detail_week_chart)
        prevWeekButton = findViewById(R.id.prev_week)
        nextWeekButton = findViewById(R.id.next_week)

        prevWeekButton.setOnClickListener { changeWeek(weeksBack + 1) }
        nextWeekButton.setOnClickListener { changeWeek(weeksBack - 1) }

        loadWeek()
    }

    private fun changeWeek(newWeeksBack: Int) {
        weeksBack = newWeeksBack.coerceAtLeast(0)
        loadWeek()
    }

    private fun loadWeek() {
        val monday = DateRange.ThisWeek.start - weeksBack * 7L * DateRange.DAY_MS
        weekDayStarts = LongArray(7) { monday + it * DateRange.DAY_MS }
        val starts = weekDayStarts
        val pkg = targetPackage

        nextWeekButton.isEnabled = weeksBack > 0
        nextWeekButton.alpha = if (nextWeekButton.isEnabled) 1f else 0.3f
        dayDetailView.text = ""

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val todayStart = DateRange.startOfToday()
            val values = withContext(Dispatchers.IO) {
                LongArray(7) { day ->
                    history.dayUsageFor(pkg, starts[day], todayStart)
                }
            }

            weekTotalView.text = TimeFormat.format(this@AppDetailActivity, values.sum())
            val dayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            weekLabelView.text = getString(
                R.string.range_between,
                dayFormat.format(Date(starts[0])),
                dayFormat.format(Date(starts[6]))
            )

            val todayIndex = if (weeksBack == 0) {
                ((DateRange.startOfToday() - starts[0]) / DateRange.DAY_MS)
                    .toInt().coerceIn(0, 6)
            } else {
                -1
            }
            val dayLabels = starts.map {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it))
            }
            weekChart.setData(values, dayLabels, todayIndex)
            weekChart.onDaySelected = { index ->
                dayDetailView.text = getString(
                    R.string.day_detail,
                    SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                        .format(Date(starts[index])),
                    TimeFormat.format(this@AppDetailActivity, values[index])
                )
                dayDetailView.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package_name"
        private const val EXTRA_LABEL = "label"

        fun launch(context: Context, app: AppUsage) {
            context.startActivity(
                Intent(context, AppDetailActivity::class.java)
                    .putExtra(EXTRA_PACKAGE, app.packageName)
                    .putExtra(EXTRA_LABEL, app.label)
            )
        }
    }
}
