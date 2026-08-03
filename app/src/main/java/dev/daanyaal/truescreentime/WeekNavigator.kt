package dev.daanyaal.truescreentime

/**
 * Which day the week view is showing: how many whole weeks back from the
 * current one, and the day inside that week (0 = Monday .. 6 = Sunday).
 */
data class WeekPosition(val weekOffset: Int, val dayIndex: Int)

/**
 * Day/week arithmetic for the week view. Every operation goes through an
 * absolute day-start timestamp, so stepping off either end of a week rolls
 * into the neighbouring one instead of dead-ending — the bug that made
 * "yesterday" unreachable on a Monday.
 *
 * Days are treated as fixed-length; that matches how the week view lays out
 * its seven bars.
 */
object WeekNavigator {

    /** Absolute midnight of the day a position points at. */
    fun dayStart(position: WeekPosition, currentWeekStart: Long): Long =
        currentWeekStart -
            position.weekOffset * 7L * DateRange.DAY_MS +
            position.dayIndex * DateRange.DAY_MS

    /** The inverse of [dayStart]. */
    fun positionOf(dayStart: Long, currentWeekStart: Long): WeekPosition {
        val diffDays = Math.floorDiv(dayStart - currentWeekStart, DateRange.DAY_MS).toInt()
        val dayIndex = Math.floorMod(diffDays, 7)
        return WeekPosition((dayIndex - diffDays) / 7, dayIndex)
    }

    /** Midnight of each day (Monday..Sunday) of the week `weekOffset` back. */
    fun weekDayStarts(currentWeekStart: Long, weekOffset: Int): LongArray {
        val monday = currentWeekStart - weekOffset * 7L * DateRange.DAY_MS
        return LongArray(7) { monday + it * DateRange.DAY_MS }
    }

    /**
     * Moves the selection by [deltaDays], rolling across week boundaries.
     * Backwards is unbounded; forwards stops at today.
     */
    fun step(
        position: WeekPosition,
        deltaDays: Int,
        currentWeekStart: Long,
        todayStart: Long,
    ): WeekPosition {
        val target = dayStart(position, currentWeekStart) + deltaDays * DateRange.DAY_MS
        return positionOf(minOf(target, todayStart), currentWeekStart)
    }

    /** Last day of the shown week that may be selected (never the future). */
    fun latestSelectableIndex(
        currentWeekStart: Long,
        weekOffset: Int,
        todayStart: Long,
    ): Int {
        val monday = currentWeekStart - weekOffset * 7L * DateRange.DAY_MS
        return ((todayStart - monday) / DateRange.DAY_MS).toInt().coerceIn(0, 6)
    }

    /** False once the selection has caught up with today. */
    fun canStepForward(
        position: WeekPosition,
        currentWeekStart: Long,
        todayStart: Long,
    ): Boolean = dayStart(position, currentWeekStart) < todayStart

    /** True when a day may be selected — i.e. it is not in the future. */
    fun isSelectable(
        position: WeekPosition,
        currentWeekStart: Long,
        todayStart: Long,
    ): Boolean = dayStart(position, currentWeekStart) <= todayStart
}
