package dev.daanyaal.truescreentime

import android.content.Context

/**
 * Persists which apps are excluded from the total, plus the
 * "show system apps" / "show excluded apps" preferences. Default: every
 * app included, system and excluded apps hidden from the list.
 */
class FilterStore(context: Context) {

    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM, value).apply()

    var showExcludedApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXCLUDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXCLUDED, value).apply()

    fun isIncluded(packageName: String): Boolean =
        packageName !in excludedPackages()

    fun setIncluded(packageName: String, included: Boolean) {
        // Sets returned by SharedPreferences must not be mutated in place.
        val updated = HashSet(excludedPackages())
        if (included) updated.remove(packageName) else updated.add(packageName)
        prefs.edit().putStringSet(KEY_EXCLUDED, updated).apply()
    }

    fun excludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()

    /** Clears the whole exclusion list — every app counts again. */
    fun includeAll() {
        prefs.edit().putStringSet(KEY_EXCLUDED, emptySet()).apply()
    }

    private companion object {
        const val KEY_EXCLUDED = "excluded_packages"
        const val KEY_SHOW_SYSTEM = "show_system_apps"
        const val KEY_SHOW_EXCLUDED = "show_excluded_apps"
    }
}
