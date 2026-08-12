package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonutSlicesTest {

    private fun app(name: String, minutes: Long) = AppUsage(
        packageName = name,
        label = name,
        icon = null,
        totalTimeMs = minutes * 60_000,
        isSystem = false,
    )

    private fun apps(count: Int) = List(count) { app("app$it", (count - it).toLong() * 10) }

    @Test
    fun `four or fewer apps get their own slice and no other`() {
        val slices = DonutSlices.build(apps(4), "Other", drilledIn = false)
        assertEquals(4, slices.size)
        assertFalse(slices.any { it.isOther })
        assertFalse(DonutSlices.hasOther(apps(4)))
    }

    @Test
    fun `the tail is pooled into a single other slice`() {
        val list = apps(7)
        val slices = DonutSlices.build(list, "Other", drilledIn = false)

        assertEquals(5, slices.size) // four apps plus Other
        val other = slices.last()
        assertTrue(other.isOther)
        assertEquals("Other", other.label)
        assertEquals(list.drop(4).sumOf { it.totalTimeMs }, other.totalMs)
        assertTrue(DonutSlices.hasOther(list))
    }

    @Test
    fun `drilling in shows exactly the pooled apps`() {
        val list = apps(7)
        val slices = DonutSlices.build(list, "Other", drilledIn = true)

        assertEquals(listOf("app4", "app5", "app6"), slices.map { it.label })
        assertFalse(slices.any { it.isOther })
        // The drilled donut adds up to the Other slice it came from.
        val pooled = DonutSlices.build(list, "Other", drilledIn = false).last()
        assertEquals(pooled.totalMs, slices.sumOf { it.totalMs })
    }

    @Test
    fun `drilling in with no tail yields nothing to draw`() {
        assertEquals(emptyList<DonutSlice>(), DonutSlices.build(apps(3), "Other", drilledIn = true))
    }

    @Test
    fun `no slices at all when there is no usage`() {
        assertEquals(emptyList<DonutSlice>(), DonutSlices.build(emptyList(), "Other", false))
        assertFalse(DonutSlices.hasOther(emptyList()))
    }

    @Test
    fun `slice times match their apps`() {
        val slices = DonutSlices.build(apps(3), "Other", drilledIn = false)
        assertEquals(30 * 60_000L, slices[0].totalMs)
        assertEquals(20 * 60_000L, slices[1].totalMs)
        assertEquals(10 * 60_000L, slices[2].totalMs)
    }
}
