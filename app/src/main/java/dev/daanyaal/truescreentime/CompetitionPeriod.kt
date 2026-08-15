package dev.daanyaal.truescreentime

/** Where "today" sits inside a group's fixed competition window. */
object CompetitionPeriod {

    fun dayStarts(rules: GroupRules): List<Long> =
        (0 until rules.periodDays).map { rules.periodStartDay + it * DateRange.DAY_MS }

    fun contains(dayStart: Long, rules: GroupRules): Boolean =
        dayStart >= rules.periodStartDay && dayStart <= rules.lastDayStart

    /** 0 before the period opens, capped at its length once it closes. */
    fun daysElapsed(rules: GroupRules, todayStart: Long): Int = when {
        todayStart < rules.periodStartDay -> 0
        todayStart > rules.lastDayStart -> rules.periodDays
        else -> (((todayStart - rules.periodStartDay) / DateRange.DAY_MS) + 1).toInt()
    }

    fun daysRemaining(rules: GroupRules, todayStart: Long): Int =
        rules.periodDays - daysElapsed(rules, todayStart)

    fun isComplete(rules: GroupRules, todayStart: Long): Boolean =
        todayStart > rules.lastDayStart

    fun hasStarted(rules: GroupRules, todayStart: Long): Boolean =
        todayStart >= rules.periodStartDay

    /** Days of the period that have actually happened, oldest first. */
    fun elapsedDayStarts(rules: GroupRules, todayStart: Long): List<Long> =
        dayStarts(rules).take(daysElapsed(rules, todayStart))
}

/**
 * Scores a day under a group's rules rather than the user's personal
 * filters, so every member's number is computed the same way.
 */
object CompetitionScore {

    fun dailyTotalMs(apps: List<AppUsage>, rules: GroupRules): Long = apps
        .filter { app ->
            (rules.countSystemApps || !app.isSystem) &&
                app.packageName !in rules.excludedPackages
        }
        .sumOf { it.totalTimeMs }

    fun totalMs(dailyTotals: Collection<Long>): Long = dailyTotals.sum()

    /** Average per elapsed day — the fair comparison mid-period. */
    fun dailyAverageMs(dailyTotals: List<Long>): Long =
        if (dailyTotals.isEmpty()) 0L else dailyTotals.sum() / dailyTotals.size
}
