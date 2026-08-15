package dev.daanyaal.truescreentime

import android.content.Context

/**
 * The app's single source of usage data: recent days come from the OS and
 * are archived as a side effect of being read; older days come from the
 * archive. That is what lets the week view walk back past Android's own
 * retention window.
 *
 * Disk and system calls throughout — call from a background thread.
 */
class UsageHistory(
    private val repository: UsageStatsRepository,
    private val archive: UsageArchive,
) {

    constructor(context: Context) : this(
        UsageStatsRepository(context),
        UsageArchive(context),
    )

    fun hasUsageAccess(): Boolean = repository.hasUsageAccess()

    /**
     * Usage for a single day. Live days are re-read from the OS (they are
     * fresher, and today is still changing) and written through to the
     * archive; if the OS has nothing — pruned early, or the day is old —
     * the archive answers instead.
     */
    fun dayUsage(dayStart: Long, todayStart: Long): List<AppUsage> {
        if (ArchivePolicy.isLive(dayStart, todayStart)) {
            val live = repository.loadAppUsages(dayStart, dayStart + DateRange.DAY_MS)
            if (live.isNotEmpty()) {
                archive.saveDay(dayStart, live)
                return live
            }
        }
        return archive.loadDay(dayStart).map { stored ->
            AppUsage(
                packageName = stored.packageName,
                label = stored.label,
                icon = repository.iconFor(stored.packageName),
                totalTimeMs = stored.totalMs,
                isSystem = stored.isSystem,
            )
        }
    }

    /**
     * Usage across a day-aligned range, summed per package. [end] is
     * exclusive; a partial final day (i.e. today) is handled by the day
     * lookup itself, which clamps to now.
     */
    fun rangeUsage(start: Long, end: Long, todayStart: Long): List<AppUsage> {
        val totals = HashMap<String, Long>()
        val details = HashMap<String, AppUsage>()

        var day = start
        while (day < end) {
            for (app in dayUsage(day, todayStart)) {
                totals[app.packageName] = (totals[app.packageName] ?: 0L) + app.totalTimeMs
                details[app.packageName] = app
            }
            day += DateRange.DAY_MS
        }

        return totals.map { (pkg, total) ->
            details.getValue(pkg).copy(totalTimeMs = total)
        }.sortedByDescending { it.totalTimeMs }
    }

    /** Foreground time for one package on one day. */
    fun dayUsageFor(packageName: String, dayStart: Long, todayStart: Long): Long =
        dayUsage(dayStart, todayStart)
            .firstOrNull { it.packageName == packageName }
            ?.totalTimeMs
            ?: 0L

    /**
     * Snapshots every day the OS can still answer for, so history survives
     * once Android forgets it, and drops anything past the retention limit.
     * Cheap enough to run whenever the app is opened.
     */
    fun archiveLiveWindow(todayStart: Long) {
        for (dayStart in ArchivePolicy.daysToArchive(todayStart)) {
            val live = repository.loadAppUsages(dayStart, dayStart + DateRange.DAY_MS)
            archive.saveDay(dayStart, live)
        }
        archive.deleteBefore(ArchivePolicy.pruneBefore(todayStart))
    }

    fun archivedDayCount(): Int = archive.archivedDayCount()
}
