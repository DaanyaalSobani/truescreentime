package dev.daanyaal.truescreentime

/**
 * Decides which days can still be read from the OS and which can only come
 * from our own archive. Android keeps detailed usage events for roughly a
 * week, so everything older is ours to remember or lose.
 */
object ArchivePolicy {

    /** Days back (including today) we still trust the OS to answer for. */
    const val LIVE_WINDOW_DAYS = 7

    /** How much history the archive keeps before pruning. */
    const val RETENTION_DAYS = 400

    fun isLive(dayStart: Long, todayStart: Long): Boolean =
        dayStart <= todayStart &&
            dayStart > todayStart - LIVE_WINDOW_DAYS * DateRange.DAY_MS

    /** Day starts, newest first, that should be snapshotted on app open. */
    fun daysToArchive(todayStart: Long): List<Long> =
        (0 until LIVE_WINDOW_DAYS).map { todayStart - it * DateRange.DAY_MS }

    fun pruneBefore(todayStart: Long): Long =
        todayStart - RETENTION_DAYS * DateRange.DAY_MS
}
