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
import androidx.activity.OnBackPressedCallback
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

    private lateinit var history: UsageHistory
    private lateinit var filterStore: FilterStore
    private lateinit var adapter: AppUsageAdapter

    private lateinit var permissionContainer: View
    private lateinit var contentContainer: View
    private lateinit var donutCard: View
    private lateinit var donutChart: DonutChartView
    private lateinit var donutDetailView: TextView
    private lateinit var donutBackButton: Button
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

    private var donutDrilledIn = false
    private var donutSlices: List<DonutSlice> = emptyList()
    private lateinit var backCallback: OnBackPressedCallback

    private var rangeList: List<AppUsage> = emptyList()
    private var weekLists: List<List<AppUsage>> = emptyList()
    private var weekDayStarts = LongArray(0)
    private var weekPosition = WeekPosition(0, 0)
    private var suppressToggleListener = false
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        history = UsageHistory(this)
        filterStore = FilterStore(this)

        permissionContainer = findViewById(R.id.permission_container)
        contentContainer = findViewById(R.id.content_container)
        donutCard = findViewById(R.id.donut_card)
        donutChart = findViewById(R.id.donut_chart)
        donutDetailView = findViewById(R.id.donut_detail)
        donutBackButton = findViewById(R.id.donut_back)
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

        donutChart.onSegmentTapped = { index ->
            donutSlices.getOrNull(index)?.let { slice ->
                donutDetailView.text = getString(
                    R.string.slice_detail,
                    slice.label,
                    TimeFormat.format(this, slice.totalMs)
                )
                donutChart.setSelectedSegment(index)
            }
        }
        donutChart.onSegmentDoubleTapped = { index ->
            // Only the pooled tail can be opened up.
            if (donutSlices.getOrNull(index)?.isOther == true) {
                donutDrilledIn = true
                donutChart.setSelectedSegment(-1)
                refreshDisplayedData()
            }
        }
        donutBackButton.setOnClickListener { exitDonutDrill() }
        // While drilled in, system back returns to the full donut.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = exitDonutDrill()
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        weekChart.onDaySelected = { index -> selectDay(index) }
        prevDayButton.setOnClickListener { stepDay(-1) }
        nextDayButton.setOnClickListener { stepDay(1) }

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
                    // Start on today, in the current week.
                    weekPosition = WeekNavigator.positionOf(
                        DateRange.startOfToday(), DateRange.ThisWeek.start
                    )
                    loadData()
                }
                R.id.button_custom -> pickCustomRange()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = history.hasUsageAccess()
        permissionContainer.visibility = if (granted) View.GONE else View.VISIBLE
        contentContainer.visibility = if (granted) View.VISIBLE else View.GONE
        if (granted) {
            // Capture whatever the OS still remembers before it drops it.
            lifecycleScope.launch(Dispatchers.IO) {
                history.archiveLiveWindow(DateRange.startOfToday())
            }
            loadData()
        }
    }

    private fun loadData() {
        // New data invalidates any "Other" breakdown on screen.
        donutDrilledIn = false
        donutChart.setSelectedSegment(-1)
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val todayStart = DateRange.startOfToday()
            when (mode) {
                Mode.TODAY -> {
                    rangeList = withContext(Dispatchers.IO) {
                        history.dayUsage(todayStart, todayStart)
                    }
                }
                Mode.CUSTOM -> {
                    val start = customStart
                    val end = customEnd
                    rangeList = withContext(Dispatchers.IO) {
                        history.rangeUsage(start, end, todayStart)
                    }
                }
                Mode.WEEK -> {
                    val starts = WeekNavigator.weekDayStarts(
                        DateRange.ThisWeek.start, weekPosition.weekOffset
                    )
                    weekDayStarts = starts
                    weekLists = withContext(Dispatchers.IO) {
                        starts.map { dayStart -> history.dayUsage(dayStart, todayStart) }
                    }
                }
            }
            refreshDisplayedData()
        }
    }

    /** Steps the selection a day at a time, rolling between weeks. */
    private fun stepDay(deltaDays: Int) {
        applyPosition(
            WeekNavigator.step(
                weekPosition, deltaDays, DateRange.ThisWeek.start, DateRange.startOfToday()
            )
        )
    }

    /** Selects a day within the week currently on screen (bar taps). */
    private fun selectDay(index: Int) {
        val candidate = weekPosition.copy(dayIndex = index)
        if (WeekNavigator.isSelectable(
                candidate, DateRange.ThisWeek.start, DateRange.startOfToday()
            )
        ) {
            applyPosition(candidate)
        }
    }

    /** Reloads only when the week changed; otherwise redraws from cache. */
    private fun applyPosition(position: WeekPosition) {
        val weekChanged = position.weekOffset != weekPosition.weekOffset
        weekPosition = position
        if (weekChanged) {
            loadData()
        } else {
            // A different day means a different breakdown.
            donutDrilledIn = false
            donutChart.setSelectedSegment(-1)
            refreshDisplayedData()
        }
    }

    /** Re-applies filters to cached data and redraws totals, charts, list. */
    private fun refreshDisplayedData() {
        val selectedDayIndex = weekPosition.dayIndex
        val currentList = if (mode == Mode.WEEK) {
            weekLists.getOrElse(selectedDayIndex) { emptyList() }
        } else {
            rangeList
        }
        val filtered = filter(currentList)
        adapter.submitList(filtered.visible)
        emptyView.visibility = if (filtered.visible.isEmpty()) View.VISIBLE else View.GONE

        val included = filtered.included
        val totalText = TimeFormat.format(this, filtered.totalMs)
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
                filter(weekLists.getOrElse(day) { emptyList() }).totalMs
            }
            val dayLabels = weekDayStarts.map {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it))
            }
            weekChart.setData(barValues, dayLabels, selectedDayIndex)

            // Back is always possible; forward stops once we reach today.
            prevDayButton.isEnabled = true
            nextDayButton.isEnabled = WeekNavigator.canStepForward(
                weekPosition, DateRange.ThisWeek.start, DateRange.startOfToday()
            )
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

    private fun filter(apps: List<AppUsage>): FilteredApps = AppListFilter.apply(
        apps,
        showSystemApps = filterStore.showSystemApps,
        showExcludedApps = filterStore.showExcludedApps,
        isIncluded = filterStore::isIncluded,
    )

    /** Top apps as donut segments, with the remainder folded into "Other". */
    private fun updateDonut(title: String, totalText: String, included: List<AppUsage>) {
        // The tail can vanish as filters change; fall back to the full donut.
        if (donutDrilledIn && !DonutSlices.hasOther(included)) donutDrilledIn = false

        val slices = DonutSlices.build(included, getString(R.string.other), donutDrilledIn)
        donutSlices = slices

        val centerTitle = if (donutDrilledIn) getString(R.string.other) else title
        val centerValue = if (donutDrilledIn) {
            TimeFormat.format(this, slices.sumOf { it.totalMs })
        } else {
            totalText
        }
        donutChart.setData(
            slices.map { DonutChartView.Segment(it.label, it.totalMs) },
            centerTitle,
            centerValue,
        )

        donutBackButton.visibility = if (donutDrilledIn) View.VISIBLE else View.GONE
        backCallback.isEnabled = donutDrilledIn
        donutDetailView.text = when {
            donutDrilledIn -> getString(R.string.donut_hint)
            DonutSlices.hasOther(included) -> getString(R.string.donut_hint_other)
            else -> getString(R.string.donut_hint)
        }
    }

    /** Leaves the "Other" breakdown and redraws the full donut. */
    private fun exitDonutDrill() {
        if (!donutDrilledIn) return
        donutDrilledIn = false
        donutChart.setSelectedSegment(-1)
        refreshDisplayedData()
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
