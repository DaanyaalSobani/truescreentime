package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day/week stepping, including the case that shipped broken: on a Monday
 * the chevrons must still reach yesterday, which lives in the previous week.
 */
class WeekNavigatorTest {

    private val day = DateRange.DAY_MS
    private val weekStart = 1_800_000_000_000L // arbitrary "Monday 00:00"

    @Test
    fun `day start maps back and forth`() {
        val position = WeekPosition(weekOffset = 0, dayIndex = 3)
        val start = WeekNavigator.dayStart(position, weekStart)
        assertEquals(weekStart + 3 * day, start)
        assertEquals(position, WeekNavigator.positionOf(start, weekStart))
    }

    @Test
    fun `sunday of last week is the day before this monday`() {
        val position = WeekPosition(weekOffset = 1, dayIndex = 6)
        assertEquals(weekStart - day, WeekNavigator.dayStart(position, weekStart))
        assertEquals(position, WeekNavigator.positionOf(weekStart - day, weekStart))
    }

    @Test
    fun `stepping back from monday rolls into the previous week`() {
        // Today is Monday: this is the case that used to dead-end.
        val today = weekStart
        val monday = WeekPosition(0, 0)
        val back = WeekNavigator.step(monday, -1, weekStart, today)
        assertEquals(WeekPosition(1, 6), back)
        assertEquals(today - day, WeekNavigator.dayStart(back, weekStart))
    }

    @Test
    fun `stepping forward from sunday rolls into the following week`() {
        val today = weekStart + 2 * day
        val lastSunday = WeekPosition(1, 6)
        assertEquals(
            WeekPosition(0, 0),
            WeekNavigator.step(lastSunday, 1, weekStart, today)
        )
    }

    @Test
    fun `stepping forward stops at today`() {
        val today = weekStart + 2 * day
        val atToday = WeekPosition(0, 2)
        assertEquals(atToday, WeekNavigator.step(atToday, 1, weekStart, today))
    }

    @Test
    fun `large forward step is clamped to today`() {
        val today = weekStart + 2 * day
        val position = WeekPosition(3, 0)
        assertEquals(
            WeekPosition(0, 2),
            WeekNavigator.step(position, 30, weekStart, today)
        )
    }

    @Test
    fun `stepping back is unbounded`() {
        var position = WeekPosition(0, 0)
        repeat(20) { position = WeekNavigator.step(position, -1, weekStart, weekStart) }
        assertEquals(weekStart - 20 * day, WeekNavigator.dayStart(position, weekStart))
    }

    @Test
    fun `week day starts are seven consecutive days`() {
        val starts = WeekNavigator.weekDayStarts(weekStart, weekOffset = 2)
        assertEquals(7, starts.size)
        assertEquals(weekStart - 14 * day, starts[0])
        for (i in 1 until 7) {
            assertEquals(day, starts[i] - starts[i - 1])
        }
    }

    @Test
    fun `latest selectable index is today in this week and sunday in older ones`() {
        val today = weekStart + 3 * day // Thursday
        assertEquals(3, WeekNavigator.latestSelectableIndex(weekStart, 0, today))
        assertEquals(6, WeekNavigator.latestSelectableIndex(weekStart, 1, today))
    }

    @Test
    fun `cannot step forward past today but can before it`() {
        val today = weekStart + 3 * day
        assertFalse(WeekNavigator.canStepForward(WeekPosition(0, 3), weekStart, today))
        assertTrue(WeekNavigator.canStepForward(WeekPosition(0, 2), weekStart, today))
        assertTrue(WeekNavigator.canStepForward(WeekPosition(1, 6), weekStart, today))
    }

    @Test
    fun `future days are not selectable`() {
        val today = weekStart + 1 * day // Tuesday
        assertTrue(WeekNavigator.isSelectable(WeekPosition(0, 1), weekStart, today))
        assertFalse(WeekNavigator.isSelectable(WeekPosition(0, 5), weekStart, today))
        assertTrue(WeekNavigator.isSelectable(WeekPosition(1, 5), weekStart, today))
    }
}
