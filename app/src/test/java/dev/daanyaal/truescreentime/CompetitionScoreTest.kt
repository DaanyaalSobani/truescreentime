package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Test

class CompetitionScoreTest {

    private fun app(pkg: String, minutes: Long, isSystem: Boolean = false) = AppUsage(
        packageName = pkg,
        label = pkg,
        icon = null,
        totalTimeMs = minutes * 60_000,
        isSystem = isSystem,
    )

    private fun rules(
        excluded: Set<String> = setOf("maps"),
        countSystem: Boolean = false,
    ) = GroupRules(
        groupId = "G1",
        name = "Contest",
        periodStartDay = 0,
        periodDays = 14,
        timeZoneId = "UTC",
        excludedPackages = excluded,
        countSystemApps = countSystem,
        sharingLevel = SharingLevel.TOTAL_ONLY,
        secretHex = "FF",
    )

    private val apps = listOf(
        app("chat", 60),
        app("maps", 45),
        app("launcher", 20, isSystem = true),
    )

    @Test
    fun `group exclusions are left out of the score`() {
        assertEquals(60 * 60_000L, CompetitionScore.dailyTotalMs(apps, rules()))
    }

    @Test
    fun `system apps only count when the group says so`() {
        assertEquals(
            80 * 60_000L,
            CompetitionScore.dailyTotalMs(apps, rules(countSystem = true)),
        )
    }

    @Test
    fun `personal filters are irrelevant — only the group rules apply`() {
        // The same app list scored under two different rule sets must differ
        // solely because of the rules, never because of local preferences.
        val strict = CompetitionScore.dailyTotalMs(apps, rules(excluded = setOf("maps", "chat")))
        val loose = CompetitionScore.dailyTotalMs(apps, rules(excluded = emptySet()))
        assertEquals(0L, strict)
        assertEquals(105 * 60_000L, loose)
    }

    @Test
    fun `totals and averages add up over the period`() {
        val daily = listOf(60L, 30L, 90L).map { it * 60_000 }
        assertEquals(180 * 60_000L, CompetitionScore.totalMs(daily))
        assertEquals(60 * 60_000L, CompetitionScore.dailyAverageMs(daily))
    }

    @Test
    fun `an empty period averages zero rather than dividing by zero`() {
        assertEquals(0L, CompetitionScore.dailyAverageMs(emptyList()))
    }
}
