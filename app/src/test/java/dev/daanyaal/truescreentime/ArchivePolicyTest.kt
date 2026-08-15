package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePolicyTest {

    private val day = DateRange.DAY_MS
    private val today = 1_800_000_000_000L

    @Test
    fun `today and the last six days are still live`() {
        assertTrue(ArchivePolicy.isLive(today, today))
        assertTrue(ArchivePolicy.isLive(today - 6 * day, today))
    }

    @Test
    fun `a week back is past the live window`() {
        assertFalse(ArchivePolicy.isLive(today - 7 * day, today))
        assertFalse(ArchivePolicy.isLive(today - 30 * day, today))
    }

    @Test
    fun `future days are never live`() {
        assertFalse(ArchivePolicy.isLive(today + day, today))
    }

    @Test
    fun `the archive sweep covers exactly the live window`() {
        val days = ArchivePolicy.daysToArchive(today)
        assertEquals(ArchivePolicy.LIVE_WINDOW_DAYS, days.size)
        assertEquals(today, days.first())
        assertEquals(today - 6 * day, days.last())
        assertTrue(days.all { ArchivePolicy.isLive(it, today) })
    }

    @Test
    fun `pruning keeps roughly a year of history`() {
        val cutoff = ArchivePolicy.pruneBefore(today)
        assertTrue(cutoff < today - 365 * day)
        assertFalse(ArchivePolicy.isLive(cutoff, today))
    }
}
