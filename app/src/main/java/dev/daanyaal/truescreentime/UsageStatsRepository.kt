package dev.daanyaal.truescreentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process

data class AppUsage(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val totalTimeMs: Long,
    val isSystem: Boolean,
)

/**
 * Reads foreground time per package by replaying UsageEvents
 * (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND, a.k.a. ACTIVITY_RESUMED /
 * ACTIVITY_PAUSED, plus ACTIVITY_STOPPED on API 29+) instead of trusting
 * the pre-bucketed totals from queryUsageStats().
 */
class UsageStatsRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Reconstructs foreground time per package inside [rangeStart, rangeEnd].
     *
     * The event query starts a few hours before rangeStart so a session that
     * was already in the foreground at rangeStart still gets counted; every
     * accumulated slice is clamped to the requested range.
     */
    fun queryForegroundTimes(rangeStart: Long, rangeEnd: Long): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val effectiveEnd = minOf(rangeEnd, now)
        if (effectiveEnd <= rangeStart) return emptyMap()

        val lookback = rangeStart - LOOKBACK_MS
        val events = usm.queryEvents(lookback, effectiveEnd)

        val totals = HashMap<String, Long>()
        // Per package: the set of activity classes currently resumed, and the
        // timestamp at which the package first came to the foreground.
        val resumedClasses = HashMap<String, MutableSet<String>>()
        val foregroundSince = HashMap<String, Long>()
        val event = UsageEvents.Event()

        fun accumulate(pkg: String, from: Long, to: Long) {
            val start = maxOf(from, rangeStart)
            val end = minOf(to, effectiveEnd)
            if (end > start) totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val cls = event.className ?: pkg
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { // == ACTIVITY_RESUMED
                    val classes = resumedClasses.getOrPut(pkg) { HashSet() }
                    if (classes.isEmpty()) foregroundSince[pkg] = event.timeStamp
                    classes.add(cls)
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND, // == ACTIVITY_PAUSED
                ACTIVITY_STOPPED -> {
                    val classes = resumedClasses[pkg] ?: continue
                    // Removing by class name keeps this idempotent when both
                    // PAUSED and STOPPED arrive for the same activity.
                    if (classes.remove(cls) && classes.isEmpty()) {
                        foregroundSince.remove(pkg)?.let { since ->
                            accumulate(pkg, since, event.timeStamp)
                        }
                    }
                }
            }
        }

        // Anything still in the foreground when the range ends.
        for ((pkg, since) in foregroundSince) {
            accumulate(pkg, since, effectiveEnd)
        }
        return totals
    }

    /** Resolves labels/icons and system-app status, sorted by time descending. */
    fun loadAppUsages(rangeStart: Long, rangeEnd: Long): List<AppUsage> {
        val pm = context.packageManager
        val launcherPackage = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

        return queryForegroundTimes(rangeStart, rangeEnd)
            .asSequence()
            .filter { it.value >= MIN_TIME_MS } // hide zero/negligible usage
            .map { (pkg, time) ->
                val appInfo: ApplicationInfo? = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                val isSystem = when {
                    pkg == launcherPackage -> true
                    appInfo == null -> false
                    else -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                }
                AppUsage(
                    packageName = pkg,
                    label = appInfo?.loadLabel(pm)?.toString() ?: pkg,
                    icon = appInfo?.loadIcon(pm),
                    totalTimeMs = time,
                    isSystem = isSystem,
                )
            }
            .sortedByDescending { it.totalTimeMs }
            .toList()
    }

    companion object {
        // UsageEvents.Event.ACTIVITY_STOPPED — constant exists since API 29,
        // referenced by value so the code path is a no-op on older devices.
        private const val ACTIVITY_STOPPED = 23
        private const val LOOKBACK_MS = 6L * 60 * 60 * 1000 // 6 hours
        private const val MIN_TIME_MS = 1000L // < 1s counts as "no usage"
    }
}
