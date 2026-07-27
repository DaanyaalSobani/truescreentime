package dev.daanyaal.truescreentime

/**
 * A minimal, OS-independent projection of a UsageEvents.Event — just enough
 * to reconstruct foreground sessions. Keeping this decoupled from the
 * framework class makes the replay logic unit-testable on a plain JVM.
 */
data class ForegroundEvent(
    val packageName: String,
    val className: String?,
    val type: Type,
    val timestamp: Long,
) {
    enum class Type { RESUMED, PAUSED_OR_STOPPED }
}

/**
 * Pure function that reconstructs per-package foreground time from an
 * event stream. Every accumulated slice is clamped to [rangeStart, rangeEnd],
 * so callers may feed events from before the range (lookback) to catch
 * sessions already in progress at rangeStart.
 */
object ForegroundSessionReplay {

    fun replay(
        events: Iterable<ForegroundEvent>,
        rangeStart: Long,
        rangeEnd: Long,
    ): Map<String, Long> {
        if (rangeEnd <= rangeStart) return emptyMap()

        val totals = HashMap<String, Long>()
        // Per package: the set of activity classes currently resumed, and the
        // timestamp at which the package first came to the foreground.
        val resumedClasses = HashMap<String, MutableSet<String>>()
        val foregroundSince = HashMap<String, Long>()

        fun accumulate(pkg: String, from: Long, to: Long) {
            val start = maxOf(from, rangeStart)
            val end = minOf(to, rangeEnd)
            if (end > start) totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
        }

        for (event in events) {
            val pkg = event.packageName
            val cls = event.className ?: pkg
            when (event.type) {
                ForegroundEvent.Type.RESUMED -> {
                    val classes = resumedClasses.getOrPut(pkg) { HashSet() }
                    if (classes.isEmpty()) foregroundSince[pkg] = event.timestamp
                    classes.add(cls)
                }
                ForegroundEvent.Type.PAUSED_OR_STOPPED -> {
                    val classes = resumedClasses[pkg] ?: continue
                    // Removing by class name keeps this idempotent when both
                    // PAUSED and STOPPED arrive for the same activity.
                    if (classes.remove(cls) && classes.isEmpty()) {
                        foregroundSince.remove(pkg)?.let { since ->
                            accumulate(pkg, since, event.timestamp)
                        }
                    }
                }
            }
        }

        // Anything still in the foreground when the range ends.
        for ((pkg, since) in foregroundSince) {
            accumulate(pkg, since, rangeEnd)
        }
        return totals
    }
}
