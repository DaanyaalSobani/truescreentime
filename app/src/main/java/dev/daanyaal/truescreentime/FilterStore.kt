package dev.daanyaal.truescreentime

import android.content.Context

/**
 * Persists which apps are excluded from the total, plus the
 * "show system apps" preference. Default: every app included,
 * system apps hidden.
 */
class FilterStore(context: Context) {

    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM, value).apply()

    fun isIncluded(packageName: String): Boolean =
        packageName !in excludedPackages()

    fun setIncluded(packageName: String, included: Boolean) {
        // Sets returned by SharedPreferences must not be mutated in place.
        val updated = HashSet(excludedPackages())
        if (included) updated.remove(packageName) else updated.add(packageName)
        prefs.edit().putStringSet(KEY_EXCLUDED, updated).apply()
    }

    private fun excludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()

    private companion object {
        const val KEY_EXCLUDED = "excluded_packages"
        const val KEY_SHOW_SYSTEM = "show_system_apps"
    }
}
