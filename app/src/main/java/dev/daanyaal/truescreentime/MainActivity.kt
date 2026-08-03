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
import android.widget.Toast
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
    private lateinit var weekRangeView: TextView
    private lateinit var weekChart: WeeklyBarChartView
    private lateinit var dayLabelView: TextView
    private lateinit var prevDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var emptyView: TextView
    private lateinit var rangeToggle: MaterialButtonToggleGroup
    private lateinit var showSystemCheckBox: CheckBox
    private lateinit var showExcludedCheckBox: CheckBox

    private var mode = Mode.TODAY
    private var customStart = 0L
    private var customEnd = 0L

    private var rangeList: List<AppUsage> = emptyList()
    private var weekLists: List<List<AppUsage>> = emptyList()
    private var weekDayStarts = LongArray(0)
    private var selectedDayIndex = 0
    private var weekOffset = 0 // whole weeks back from the current one
    private var pendingSelection: Int? = null // day to select after a week change
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
        weekRangeView = findViewById(R.id.week_range)
        weekChart = findViewById(R.id.week_chart)
        dayLabelView = findViewById(R.id.day_label)
        prevDayButton = findViewById(R.id.prev_day)
        nextDayButton = findViewById(R.id.next_day)
        emptyView = findViewById(R.id.empty_view)
        rangeToggle = findViewById(R.id.range_toggle)
        showSystemCheckBox = findViewById(R.id.show_system_apps)
        showExcludedCheckBox = findViewById(R.id.show_excluded_apps)

        findViewById<Button>(R.id.grant_access_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        adapter = AppUsageAdapter(
            filterStore,
            onInclusionChanged = { app, included ->
                // Excluding hides the row unless excluded apps are shown,
                // so say where it went.
                if (!included && !filterStore.showExcludedApps) {
                    Toast.makeText(
                        this,
                        getString(R.string.excluded_toast, app.label),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                refreshDisplayedData()
            },
            onAppClicked = { app -> AppDetailActivity.launch(this, app) },
        )
        val recycler = findViewById<RecyclerView>(R.id.app_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false

        showSystemCheckBox.isChecked = filterStore.showSystemApps
        showSystemCheckBox.setOnCheckedChangeListener { _, checked ->
            filterStore.showSystemApps = checked
            refreshDisplayedData()
        }

        showExcludedCheckBox.isChecked = filterStore.showExcludedApps
        showExcludedCheckBox.setOnCheckedChangeListener { _, checked ->
            filterStore.showExcludedApps = checked
            refreshDisplayedData()
        }

        findViewById<Button>(R.id.manage_excluded).setOnClickListener {
            ExcludedAppsActivity.launch(this)
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
                    weekOffset = 0
                    pendingSelection = null
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
                    val monday = DateRange.ThisWeek.start -
                        weekOffset * 7L * DateRange.DAY_MS
                    val starts = LongArray(7) { monday + it * DateRange.DAY_MS }
                    weekDayStarts = starts
                    weekLists = withContext(Dispatchers.IO) {
                        starts.map { dayStart ->
                            repository.loadAppUsages(dayStart, dayStart + DateRange.DAY_MS)
                        }
                    }
                    selectedDayIndex =
                        (pendingSelection ?: latestSelectableIndex())
                            .coerceIn(0, latestSelectableIndex())
                    pendingSelection = null
                }
            }
            refreshDisplayedData()
        }
    }

    /**
     * Last day of the shown week the user may select: today in the current
     * week, Sunday in any earlier week (no selecting days in the future).
     */
    private fun latestSelectableIndex(): Int {
        if (weekDayStarts.isEmpty()) return 0
        val today = DateRange.startOfToday()
        return ((today - weekDayStarts[0]) / DateRange.DAY_MS).toInt().coerceIn(0, 6)
    }

    /**
     * Moves the day selection, rolling into the neighbouring week when it
     * runs off either end so the chevrons never dead-end mid-history.
     */
    private fun selectDay(index: Int) {
        when {
            index < 0 -> {
                weekOffset += 1
                pendingSelection = 6 // Sunday of the earlier week
                loadData()
            }
            index > latestSelectableIndex() -> {
                if (weekOffset > 0) {
                    weekOffset -= 1
                    pendingSelection = 0 // Monday of the later week
                    loadData()
                }
                // Already at today: nothing newer to show.
            }
            else -> {
                selectedDayIndex = index
                refreshDisplayedData()
            }
        }
    }

    /** Re-applies filters to cached data and redraws totals, charts, list. */
    private fun refreshDisplayedData() {
        val showSystem = filterStore.showSystemApps
        val currentList = if (mode == Mode.WEEK) {
            weekLists.getOrElse(selectedDayIndex) { emptyList() }
        } else {
            rangeList
        }
        val inRange = if (showSystem) currentList else currentList.filter { !it.isSystem }
        val included = inRange.filter { filterStore.isIncluded(it.packageName) }
        // Excluded apps drop out of the list entirely unless asked for.
        val visible = if (filterStore.showExcludedApps) inRange else included
        adapter.submitList(visible)
        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        val totalText = TimeFormat.format(this, included.sumOf { it.totalTimeMs })
        donutCard.visibility = View.VISIBLE

        if (mode == Mode.WEEK) {
            weekCard.visibility = View.VISIBLE

            weekTotalView.text = totalText
            weekSubtitleView.text = relativeDayLabel(selectedDayIndex)
            weekRangeView.text = getString(
                R.string.range_between,
                formatDay(0, "MMM d"),
                formatDay(6, "MMM d")
            )
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

            // Back is always possible; forward stops once we reach today.
            prevDayButton.isEnabled = true
            nextDayButton.isEnabled =
                weekOffset > 0 || selectedDayIndex < latestSelectableIndex()
            prevDayButton.alpha = 1f
            nextDayButton.alpha = if (nextDayButton.isEnabled) 1f else 0.3f

            // The donut follows whichever bar is selected.
            updateDonut(relativeDayLabel(selectedDayIndex), totalText, included)
        } else {
            weekCard.visibility = View.GONE

            val title = if (mode == Mode.TODAY) {
                getString(R.string.range_today)
            } else {
                customRangeLabel()
            }
            updateDonut(title, totalText, included)
        }
    }

    /** Top apps as donut segments, with the remainder folded into "Other". */
    private fun updateDonut(title: String, totalText: String, included: List<AppUsage>) {
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

    private fun relativeDayLabel(index: Int): String {
        val dayStart = weekDayStarts.getOrNull(index) ?: return ""
        val today = DateRange.startOfToday()
        return when (dayStart) {
            today -> getString(R.string.range_today)
            today - DateRange.DAY_MS -> getString(R.string.yesterday)
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
