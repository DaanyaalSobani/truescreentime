package dev.daanyaal.truescreentime

import dev.daanyaal.truescreentime.ForegroundEvent.Type.PAUSED_OR_STOPPED
import dev.daanyaal.truescreentime.ForegroundEvent.Type.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundSessionReplayTest {

    private val start = 1_000_000L
    private val end = 2_000_000L

    private fun resumed(pkg: String, at: Long, cls: String = "$pkg.Main") =
        ForegroundEvent(pkg, cls, RESUMED, at)

    private fun stopped(pkg: String, at: Long, cls: String = "$pkg.Main") =
        ForegroundEvent(pkg, cls, PAUSED_OR_STOPPED, at)

    @Test
    fun `simple session inside range`() {
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", start + 100), stopped("a", start + 600)),
            start, end
        )
        assertEquals(mapOf("a" to 500L), result)
    }

    @Test
    fun `empty stream yields empty map`() {
        assertEquals(emptyMap<String, Long>(), ForegroundSessionReplay.replay(emptyList(), start, end))
    }

    @Test
    fun `session straddling range start is clamped`() {
        // Resumed during the lookback window, before the range begins.
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", start - 5_000), stopped("a", start + 300)),
            start, end
        )
        assertEquals(mapOf("a" to 300L), result)
    }

    @Test
    fun `still in foreground at range end counts up to range end`() {
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", end - 400)),
            start, end
        )
        assertEquals(mapOf("a" to 400L), result)
    }

    @Test
    fun `session entirely before range start counts nothing`() {
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", start - 5_000), stopped("a", start - 4_000)),
            start, end
        )
        assertEquals(emptyMap<String, Long>(), result)
    }

    @Test
    fun `paused then stopped for the same activity does not double count`() {
        // API 29+ delivers both ACTIVITY_PAUSED and ACTIVITY_STOPPED.
        val result = ForegroundSessionReplay.replay(
            listOf(
                resumed("a", start + 100),
                stopped("a", start + 600), // paused
                stopped("a", start + 900), // stopped later, same class
            ),
            start, end
        )
        assertEquals(mapOf("a" to 500L), result)
    }

    @Test
    fun `overlapping activities of the same package count once`() {
        // Activity B resumes before A pauses (in-app navigation); the package
        // is continuously foreground from A's resume to B's stop.
        val result = ForegroundSessionReplay.replay(
            listOf(
                resumed("a", start + 100, cls = "a.First"),
                resumed("a", start + 300, cls = "a.Second"),
                stopped("a", start + 350, cls = "a.First"),
                stopped("a", start + 700, cls = "a.Second"),
            ),
            start, end
        )
        assertEquals(mapOf("a" to 600L), result)
    }

    @Test
    fun `unmatched stop without resume is ignored`() {
        val result = ForegroundSessionReplay.replay(
            listOf(stopped("a", start + 200)),
            start, end
        )
        assertEquals(emptyMap<String, Long>(), result)
    }

    @Test
    fun `interleaved apps are tracked independently`() {
        val result = ForegroundSessionReplay.replay(
            listOf(
                resumed("a", start + 100),
                stopped("a", start + 200),
                resumed("b", start + 200),
                stopped("b", start + 500),
                resumed("a", start + 500),
                stopped("a", start + 900),
            ),
            start, end
        )
        assertEquals(mapOf("a" to 500L, "b" to 300L), result)
    }

    @Test
    fun `null class name falls back to package name and still pairs up`() {
        val result = ForegroundSessionReplay.replay(
            listOf(
                ForegroundEvent("a", null, RESUMED, start + 100),
                ForegroundEvent("a", null, PAUSED_OR_STOPPED, start + 400),
            ),
            start, end
        )
        assertEquals(mapOf("a" to 300L), result)
    }

    @Test
    fun `invalid range yields empty map`() {
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", start + 100), stopped("a", start + 200)),
            end, start
        )
        assertEquals(emptyMap<String, Long>(), result)
    }

    @Test
    fun `long session spanning the whole range is capped at range length`() {
        val result = ForegroundSessionReplay.replay(
            listOf(resumed("a", start - 100_000), stopped("a", end + 100_000)),
            start, end
        )
        assertEquals(mapOf("a" to (end - start)), result)
        assertTrue(result.getValue("a") <= end - start)
    }
}
