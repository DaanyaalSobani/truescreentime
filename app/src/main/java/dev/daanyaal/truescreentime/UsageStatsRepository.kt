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
     * was already in the foreground at rangeStart still gets counted; the
     * clamping to the requested range happens in [ForegroundSessionReplay].
     */
    fun queryForegroundTimes(rangeStart: Long, rangeEnd: Long): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val effectiveEnd = minOf(rangeEnd, now)
        if (effectiveEnd <= rangeStart) return emptyMap()

        val events = usm.queryEvents(rangeStart - LOOKBACK_MS, effectiveEnd)
        val projected = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val type = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> // == ACTIVITY_RESUMED
                    ForegroundEvent.Type.RESUMED
                UsageEvents.Event.MOVE_TO_BACKGROUND, // == ACTIVITY_PAUSED
                ACTIVITY_STOPPED ->
                    ForegroundEvent.Type.PAUSED_OR_STOPPED
                else -> continue
            }
            projected += ForegroundEvent(pkg, event.className, type, event.timeStamp)
        }
        return ForegroundSessionReplay.replay(projected, rangeStart, effectiveEnd)
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
