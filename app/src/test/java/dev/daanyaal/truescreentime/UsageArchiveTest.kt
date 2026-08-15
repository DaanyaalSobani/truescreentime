package dev.daanyaal.truescreentime

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the archive against a real SQLite database via Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UsageArchiveTest {

    private val day = DateRange.DAY_MS
    private val today = 1_800_000_000_000L

    private fun newArchive() = UsageArchive(ApplicationProvider.getApplicationContext())

    private fun app(pkg: String, minutes: Long, isSystem: Boolean = false) = AppUsage(
        packageName = pkg,
        label = pkg.uppercase(),
        icon = null,
        totalTimeMs = minutes * 60_000,
        isSystem = isSystem,
    )

    @Test
    fun `a saved day comes back with its apps, longest first`() {
        val archive = newArchive()
        archive.saveDay(today, listOf(app("news", 30), app("chat", 60)))

        val stored = archive.loadDay(today)
        assertEquals(listOf("chat", "news"), stored.map { it.packageName })
        assertEquals(60 * 60_000L, stored.first().totalMs)
        assertEquals("CHAT", stored.first().label)
    }

    @Test
    fun `system flags survive the round trip`() {
        val archive = newArchive()
        archive.saveDay(today, listOf(app("launcher", 10, isSystem = true)))

        assertTrue(archive.loadDay(today).single().isSystem)
    }

    @Test
    fun `data persists across archive instances`() {
        newArchive().saveDay(today, listOf(app("chat", 42)))
        assertEquals(1, newArchive().loadDay(today).size)
    }

    @Test
    fun `saving a day again replaces it rather than accumulating`() {
        val archive = newArchive()
        archive.saveDay(today, listOf(app("chat", 30), app("news", 10)))
        archive.saveDay(today, listOf(app("chat", 45)))

        val stored = archive.loadDay(today)
        assertEquals(listOf("chat"), stored.map { it.packageName })
        assertEquals(45 * 60_000L, stored.single().totalMs)
    }

    @Test
    fun `an empty save never wipes an existing day`() {
        // The OS returning nothing may mean it pruned the day, not that the
        // phone went unused — the stored copy has to win.
        val archive = newArchive()
        archive.saveDay(today, listOf(app("chat", 30)))
        archive.saveDay(today, emptyList())

        assertEquals(1, archive.loadDay(today).size)
    }

    @Test
    fun `days are stored independently`() {
        val archive = newArchive()
        archive.saveDay(today, listOf(app("chat", 30)))
        archive.saveDay(today - day, listOf(app("news", 20), app("chat", 5)))

        assertEquals(1, archive.loadDay(today).size)
        assertEquals(2, archive.loadDay(today - day).size)
        assertEquals(2, archive.archivedDayCount())
    }

    @Test
    fun `an unknown day is simply empty`() {
        assertEquals(emptyList<ArchivedUsage>(), newArchive().loadDay(today - 99 * day))
    }

    @Test
    fun `pruning drops only days older than the cutoff`() {
        val archive = newArchive()
        archive.saveDay(today, listOf(app("chat", 30)))
        archive.saveDay(today - 10 * day, listOf(app("chat", 30)))

        archive.deleteBefore(today - 5 * day)

        assertEquals(1, archive.archivedDayCount())
        assertTrue(archive.loadDay(today).isNotEmpty())
        assertTrue(archive.loadDay(today - 10 * day).isEmpty())
    }
}
