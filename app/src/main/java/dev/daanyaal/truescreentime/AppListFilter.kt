package dev.daanyaal.truescreentime

/**
 * The three things every screen needs from a range's usage data: the rows
 * to show, the rows that count, and the total.
 */
data class FilteredApps(
    val visible: List<AppUsage>,
    val included: List<AppUsage>,
    val totalMs: Long,
)

/**
 * Applies the user's filters to a range's usage list. Excluded apps always
 * drop out of the total; they only stay in the list when explicitly shown.
 */
object AppListFilter {

    fun apply(
        apps: List<AppUsage>,
        showSystemApps: Boolean,
        showExcludedApps: Boolean,
        isIncluded: (String) -> Boolean,
    ): FilteredApps {
        val inRange = if (showSystemApps) apps else apps.filter { !it.isSystem }
        val included = inRange.filter { isIncluded(it.packageName) }
        return FilteredApps(
            visible = if (showExcludedApps) inRange else included,
            included = included,
            totalMs = included.sumOf { it.totalTimeMs },
        )
    }
}
