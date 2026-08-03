package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private fun app(pkg: String, minutes: Long, isSystem: Boolean = false) = AppUsage(
        packageName = pkg,
        label = pkg.uppercase(),
        icon = null,
        totalTimeMs = minutes * 60_000,
        isSystem = isSystem,
    )

    private val apps = listOf(
        app("chat", 60),
        app("news", 30),
        app("launcher", 20, isSystem = true),
    )

    @Test
    fun `system apps are hidden by default and excluded from the total`() {
        val result = AppListFilter.apply(
            apps, showSystemApps = false, showExcludedApps = false, isIncluded = { true }
        )
        assertEquals(listOf("chat", "news"), result.visible.map { it.packageName })
        assertEquals(90 * 60_000L, result.totalMs)
    }

    @Test
    fun `system apps count once shown`() {
        val result = AppListFilter.apply(
            apps, showSystemApps = true, showExcludedApps = false, isIncluded = { true }
        )
        assertEquals(3, result.visible.size)
        assertEquals(110 * 60_000L, result.totalMs)
    }

    @Test
    fun `excluded apps leave the list and the total`() {
        val result = AppListFilter.apply(
            apps,
            showSystemApps = false,
            showExcludedApps = false,
            isIncluded = { it != "chat" },
        )
        assertEquals(listOf("news"), result.visible.map { it.packageName })
        assertEquals(listOf("news"), result.included.map { it.packageName })
        assertEquals(30 * 60_000L, result.totalMs)
    }

    @Test
    fun `showing excluded apps keeps them visible but still uncounted`() {
        val result = AppListFilter.apply(
            apps,
            showSystemApps = false,
            showExcludedApps = true,
            isIncluded = { it != "chat" },
        )
        assertEquals(listOf("chat", "news"), result.visible.map { it.packageName })
        assertEquals(listOf("news"), result.included.map { it.packageName })
        assertEquals(30 * 60_000L, result.totalMs)
    }

    @Test
    fun `excluding everything yields a zero total`() {
        val result = AppListFilter.apply(
            apps, showSystemApps = true, showExcludedApps = false, isIncluded = { false }
        )
        assertEquals(emptyList<AppUsage>(), result.visible)
        assertEquals(0L, result.totalMs)
    }

    @Test
    fun `ordering is preserved`() {
        val result = AppListFilter.apply(
            apps, showSystemApps = true, showExcludedApps = true, isIncluded = { true }
        )
        assertEquals(listOf("chat", "news", "launcher"), result.visible.map { it.packageName })
    }
}
