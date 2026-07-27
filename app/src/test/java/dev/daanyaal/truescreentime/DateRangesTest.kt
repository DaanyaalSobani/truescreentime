package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateRangesTest {

    @Test
    fun `endOfDay is exactly one day after startOfDay`() {
        // Mid-July: no DST transition on this date in any common timezone.
        val start = DateRange.startOfDay(2026, Calendar.JULY, 15)
        val end = DateRange.endOfDay(2026, Calendar.JULY, 15)
        assertEquals(DateRange.DAY_MS, end - start)
    }

    @Test
    fun `startOfToday is in the past and within a day of now`() {
        val now = System.currentTimeMillis()
        val todayStart = DateRange.startOfToday()
        assertTrue(todayStart <= now)
        assertTrue(now - todayStart < DateRange.DAY_MS + 60 * 60 * 1000)
    }

    @Test
    fun `startOfDay is at midnight`() {
        val start = DateRange.startOfDay(2026, Calendar.JULY, 15)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `this week starts on a Monday at midnight, at most 6 days ago`() {
        val weekStart = DateRange.ThisWeek.start
        val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertTrue(System.currentTimeMillis() - weekStart < 7 * DateRange.DAY_MS)
    }
}
