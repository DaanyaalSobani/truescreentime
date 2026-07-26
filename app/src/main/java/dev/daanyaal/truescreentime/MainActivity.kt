package dev.daanyaal.truescreentime

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var repository: UsageStatsRepository
    private lateinit var filterStore: FilterStore
    private lateinit var adapter: AppUsageAdapter

    private lateinit var permissionContainer: View
    private lateinit var contentContainer: View
    private lateinit var totalTimeView: TextView
    private lateinit var rangeLabelView: TextView
    private lateinit var emptyView: TextView
    private lateinit var rangeToggle: MaterialButtonToggleGroup
    private lateinit var showSystemCheckBox: CheckBox

    private var currentRange: DateRange = DateRange.Today
    private var fullList: List<AppUsage> = emptyList()
    private var suppressToggleListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = UsageStatsRepository(this)
        filterStore = FilterStore(this)

        permissionContainer = findViewById(R.id.permission_container)
        contentContainer = findViewById(R.id.content_container)
        totalTimeView = findViewById(R.id.total_time)
        rangeLabelView = findViewById(R.id.range_label)
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

        showSystemCheckBox.isChecked = filterStore.showSystemApps
        showSystemCheckBox.setOnCheckedChangeListener { _, checked ->
            filterStore.showSystemApps = checked
            refreshDisplayedData()
        }

        rangeToggle.check(R.id.button_today)
        rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressToggleListener) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.button_today -> setRange(DateRange.Today)
                R.id.button_week -> setRange(DateRange.ThisWeek)
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

    private fun setRange(range: DateRange) {
        currentRange = range
        loadData()
    }

    /** Two sequential date pickers: start date, then end date. */
    private fun pickCustomRange() {
        val cal = Calendar.getInstance()
        val previousRange = currentRange
        DatePickerDialog(
            this,
            { _, startYear, startMonth, startDay ->
                DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDay ->
                        val start = DateRange.startOfDay(startYear, startMonth, startDay)
                        val end = DateRange.endOfDay(endYear, endMonth, endDay)
                        if (end > start) {
                            setRange(DateRange.Custom(start, end))
                        } else {
                            restoreToggleFor(previousRange)
                        }
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle(getString(R.string.pick_end_date))
                    setOnCancelListener { restoreToggleFor(previousRange) }
                }.show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(getString(R.string.pick_start_date))
            setOnCancelListener { restoreToggleFor(previousRange) }
        }.show()
    }

    private fun restoreToggleFor(range: DateRange) {
        suppressToggleListener = true
        when (range) {
            is DateRange.Today -> rangeToggle.check(R.id.button_today)
            is DateRange.ThisWeek -> rangeToggle.check(R.id.button_week)
            is DateRange.Custom -> rangeToggle.check(R.id.button_custom)
        }
        suppressToggleListener = false
    }

    private fun loadData() {
        val range = currentRange
        rangeLabelView.text = describeRange(range)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.loadAppUsages(range.start, range.end)
            }
            fullList = result
            refreshDisplayedData()
        }
    }

    /** Re-applies the system-app filter and inclusion toggles to cached data. */
    private fun refreshDisplayedData() {
        val visible = if (filterStore.showSystemApps) {
            fullList
        } else {
            fullList.filter { !it.isSystem }
        }
        adapter.submitList(visible)
        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        val totalMs = visible
            .filter { filterStore.isIncluded(it.packageName) }
            .sumOf { it.totalTimeMs }
        totalTimeView.text = TimeFormat.format(this, totalMs)
    }

    private fun describeRange(range: DateRange): String {
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        return when (range) {
            is DateRange.Today -> getString(R.string.range_today)
            is DateRange.ThisWeek -> getString(
                R.string.range_since, df.format(Date(range.start))
            )
            is DateRange.Custom -> getString(
                R.string.range_between,
                df.format(Date(range.start)),
                // end is exclusive midnight; show the last included day
                df.format(Date(range.end - 1))
            )
        }
    }
}
