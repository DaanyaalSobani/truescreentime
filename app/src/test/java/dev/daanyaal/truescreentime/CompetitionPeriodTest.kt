package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPeriodTest {

    private val day = DateRange.DAY_MS
    private val start = 1_800_000_000_000L

    private val rules = GroupRules(
        groupId = "G1",
        name = "Contest",
        periodStartDay = start,
        periodDays = 14,
        timeZoneId = "UTC",
        excludedPackages = setOf("maps"),
        countSystemApps = false,
        sharingLevel = SharingLevel.TOTAL_ONLY,
        secretHex = "FF",
    )

    @Test
    fun `the period covers exactly its days`() {
        val days = CompetitionPeriod.dayStarts(rules)
        assertEquals(14, days.size)
        assertEquals(start, days.first())
        assertEquals(start + 13 * day, days.last())
        assertEquals(rules.lastDayStart, days.last())
    }

    @Test
    fun `membership of the period is inclusive at both ends`() {
        assertTrue(CompetitionPeriod.contains(start, rules))
        assertTrue(CompetitionPeriod.contains(start + 13 * day, rules))
        assertFalse(CompetitionPeriod.contains(start - day, rules))
        assertFalse(CompetitionPeriod.contains(start + 14 * day, rules))
    }

    @Test
    fun `day one counts as one day elapsed`() {
        assertEquals(1, CompetitionPeriod.daysElapsed(rules, start))
        assertEquals(13, CompetitionPeriod.daysRemaining(rules, start))
    }

    @Test
    fun `nothing has elapsed before the period opens`() {
        assertEquals(0, CompetitionPeriod.daysElapsed(rules, start - day))
        assertFalse(CompetitionPeriod.hasStarted(rules, start - day))
        assertTrue(CompetitionPeriod.hasStarted(rules, start))
    }

    @Test
    fun `elapsed days are capped once the period closes`() {
        assertEquals(14, CompetitionPeriod.daysElapsed(rules, start + 99 * day))
        assertEquals(0, CompetitionPeriod.daysRemaining(rules, start + 99 * day))
    }

    @Test
    fun `the period completes only after its last day`() {
        assertFalse(CompetitionPeriod.isComplete(rules, rules.lastDayStart))
        assertTrue(CompetitionPeriod.isComplete(rules, rules.lastDayStart + day))
    }

    @Test
    fun `elapsed days exclude the future`() {
        val elapsed = CompetitionPeriod.elapsedDayStarts(rules, start + 2 * day)
        assertEquals(listOf(start, start + day, start + 2 * day), elapsed)
    }
}
