package dev.daanyaal.truescreentime

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class Mode { TODAY, WEEK, CUSTOM }

    private lateinit var repository: UsageStatsRepository
    private lateinit var filterStore: FilterStore
    private lateinit var adapter: AppUsageAdapter

    private lateinit var permissionContainer: View
    private lateinit var contentContainer: View
    private lateinit var donutCard: View
    private lateinit var donutChart: DonutChartView
    private lateinit var weekCard: View
    private lateinit var weekTotalView: TextView
    private lateinit var weekSubtitleView: TextView
    private lateinit var weekChart: WeeklyBarChartView
    private lateinit var dayLabelView: TextView
    private lateinit var prevDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var emptyView: TextView
    private lateinit var rangeToggle: MaterialButtonToggleGroup
    private lateinit var showSystemCheckBox: CheckBox

    private var mode = Mode.TODAY
    private var customStart = 0L
    private var customEnd = 0L

    private var rangeList: List<AppUsage> = emptyList()
    private var weekLists: List<List<AppUsage>> = emptyList()
    private var weekDayStarts = LongArray(0)
    private var selectedDayIndex = 0
    private var suppressToggleListener = false
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = UsageStatsRepository(this)
        filterStore = FilterStore(this)

        permissionContainer = findViewById(R.id.permission_container)
        contentContainer = findViewById(R.id.content_container)
        donutCard = findViewById(R.id.donut_card)
        donutChart = findViewById(R.id.donut_chart)
        weekCard = findViewById(R.id.week_card)
        weekTotalView = findViewById(R.id.week_total)
        weekSubtitleView = findViewById(R.id.week_subtitle)
        weekChart = findViewById(R.id.week_chart)
        dayLabelView = findViewById(R.id.day_label)
        prevDayButton = findViewById(R.id.prev_day)
        nextDayButton = findViewById(R.id.next_day)
        emptyView = findViewById(R.id.empty_view)
        rangeToggle = findViewById(R.id.range_toggle)
        showSystemCheckBox = findViewById(R.id.show_system_apps)

        findViewById<Button>(R.id.grant_access_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        adapter = AppUsageAdapter(filterStore) { refreshDisplayedData() }
        val recycler = findViewById<RecyclerView>(R.id.app_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false

        showSystemCheckBox.isChecked = filterStore.showSystemApps
        showSystemCheckBox.setOnCheckedChangeListener { _, checked ->
            filterStore.showSystemApps = checked
            refreshDisplayedData()
        }

        weekChart.onDaySelected = { index -> selectDay(index) }
        prevDayButton.setOnClickListener { selectDay(selectedDayIndex - 1) }
        nextDayButton.setOnClickListener { selectDay(selectedDayIndex + 1) }

        rangeToggle.check(R.id.button_today)
        rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressToggleListener) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.button_today -> {
                    mode = Mode.TODAY
                    loadData()
                }
                R.id.button_week -> {
                    mode = Mode.WEEK
                    loadData()
                }
                R.id.button_custom -> pickCustomRange()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = repository.hasUsageAccess()
        permissionContainer.visibility = if (granted) View.GONE else View.VISIBLE
        contentContainer.visibility = if (granted) View.VISIBLE else View.GONE
        if (granted) loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            when (mode) {
                Mode.TODAY -> {
                    rangeList = withContext(Dispatchers.IO) {
                        repository.loadAppUsages(DateRange.Today.start, DateRange.Today.end)
                    }
                }
                Mode.CUSTOM -> {
                    val start = customStart
                    val end = customEnd
                    rangeList = withContext(Dispatchers.IO) {
                        repository.loadAppUsages(start, end)
                    }
                }
                Mode.WEEK -> {
                    val monday = DateRange.ThisWeek.start
                    val starts = LongArray(7) { monday + it * DateRange.DAY_MS }
                    weekDayStarts = starts
                    weekLists = withContext(Dispatchers.IO) {
                        starts.map { dayStart ->
                            repository.loadAppUsages(dayStart, dayStart + DateRange.DAY_MS)
                        }
                    }
                    selectedDayIndex = todayIndexInWeek()
                }
            }
            refreshDisplayedData()
        }
    }

    private fun todayIndexInWeek(): Int {
        if (weekDayStarts.isEmpty()) return 0
        val today = DateRange.startOfToday()
        return ((today - weekDayStarts[0]) / DateRange.DAY_MS).toInt().coerceIn(0, 6)
    }

    private fun selectDay(index: Int) {
        selectedDayIndex = index.coerceIn(0, todayIndexInWeek())
        refreshDisplayedData()
    }

    /** Re-applies filters to cached data and redraws totals, charts, list. */
    private fun refreshDisplayedData() {
        val showSystem = filterStore.showSystemApps
        val currentList = if (mode == Mode.WEEK) {
            weekLists.getOrElse(selectedDayIndex) { emptyList() }
        } else {
            rangeList
        }
        val visible = if (showSystem) currentList else currentList.filter { !it.isSystem }
        adapter.submitList(visible)
        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        val included = visible.filter { filterStore.isIncluded(it.packageName) }
        val totalText = TimeFormat.format(this, included.sumOf { it.totalTimeMs })

        if (mode == Mode.WEEK) {
            donutCard.visibility = View.GONE
            weekCard.visibility = View.VISIBLE

            weekTotalView.text = totalText
            weekSubtitleView.text = relativeDayLabel(selectedDayIndex)
            dayLabelView.text = formatDay(selectedDayIndex, "EEE, MMM d")

            val barValues = LongArray(7) { day ->
                weekLists.getOrElse(day) { emptyList() }
                    .filter { (showSystem || !it.isSystem) && filterStore.isIncluded(it.packageName) }
                    .sumOf { it.totalTimeMs }
            }
            val dayLabels = weekDayStarts.map {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it))
            }
            weekChart.setData(barValues, dayLabels, selectedDayIndex)

            prevDayButton.isEnabled = selectedDayIndex > 0
            nextDayButton.isEnabled = selectedDayIndex < todayIndexInWeek()
            prevDayButton.alpha = if (prevDayButton.isEnabled) 1f else 0.3f
            nextDayButton.alpha = if (nextDayButton.isEnabled) 1f else 0.3f
        } else {
            weekCard.visibility = View.GONE
            donutCard.visibility = View.VISIBLE

            val title = if (mode == Mode.TODAY) {
                getString(R.string.range_today)
            } else {
                customRangeLabel()
            }
            val segments = mutableListOf<DonutChartView.Segment>()
            for (app in included.take(4)) {
                segments += DonutChartView.Segment(app.label, app.totalTimeMs)
            }
            val otherMs = included.drop(4).sumOf { it.totalTimeMs }
            if (otherMs > 0) {
                segments += DonutChartView.Segment(getString(R.string.other), otherMs)
            }
            donutChart.setData(segments, title, totalText)
        }
    }

    private fun relativeDayLabel(index: Int): String {
        val todayIndex = todayIndexInWeek()
        return when (index) {
            todayIndex -> getString(R.string.range_today)
            todayIndex - 1 -> getString(R.string.yesterday)
            else -> formatDay(index, "EEE, MMM d")
        }
    }

    private fun formatDay(index: Int, pattern: String): String {
        val millis = weekDayStarts.getOrElse(index) { System.currentTimeMillis() }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    private fun customRangeLabel(): String {
        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        return getString(
            R.string.range_between,
            df.format(Date(customStart)),
            // end is exclusive midnight; show the last included day
            df.format(Date(customEnd - 1))
        )
    }

    /** Two sequential date pickers: start date, then end date. */
    private fun pickCustomRange() {
        val cal = Calendar.getInstance()
        val previousMode = mode
        DatePickerDialog(
            this,
            { _, startYear, startMonth, startDay ->
                DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDay ->
                        val start = DateRange.startOfDay(startYear, startMonth, startDay)
                        val end = DateRange.endOfDay(endYear, endMonth, endDay)
                        if (end > start) {
                            mode = Mode.CUSTOM
                            customStart = start
                            customEnd = end
                            loadData()
                        } else {
                            restoreToggleFor(previousMode)
                        }
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle(getString(R.string.pick_end_date))
                    setOnCancelListener { restoreToggleFor(previousMode) }
                }.show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(getString(R.string.pick_start_date))
            setOnCancelListener { restoreToggleFor(previousMode) }
        }.show()
    }

    private fun restoreToggleFor(previousMode: Mode) {
        suppressToggleListener = true
        when (previousMode) {
            Mode.TODAY -> rangeToggle.check(R.id.button_today)
            Mode.WEEK -> rangeToggle.check(R.id.button_week)
            Mode.CUSTOM -> rangeToggle.check(R.id.button_custom)
        }
        suppressToggleListener = false
    }
}
