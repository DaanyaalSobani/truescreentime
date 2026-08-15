package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupCodecTest {

    private fun rules(
        name: String = "Sunday Club",
        excluded: Set<String> = setOf("com.google.android.apps.maps", "com.spotify.music"),
    ) = GroupRules(
        groupId = "A1B2C3D4",
        name = name,
        periodStartDay = 1_800_000_000_000L,
        periodDays = 14,
        timeZoneId = "Europe/London",
        excludedPackages = excluded,
        countSystemApps = false,
        sharingLevel = SharingLevel.TOP_APPS,
        secretHex = "0123456789ABCDEF0123456789ABCDEF",
    )

    @Test
    fun `rules survive a round trip`() {
        val original = rules()
        assertEquals(original, GroupCodec.decode(GroupCodec.encode(original)))
    }

    @Test
    fun `names with separators and unicode survive`() {
        // A pipe would split the payload if it were not escaped.
        val original = rules(name = "Ben | Ana's crew 🏆")
        assertEquals(original, GroupCodec.decode(GroupCodec.encode(original)))
    }

    @Test
    fun `an empty exclusion list round trips`() {
        val original = rules(excluded = emptySet())
        val decoded = GroupCodec.decode(GroupCodec.encode(original))
        assertEquals(emptySet<String>(), decoded?.excludedPackages)
    }

    @Test
    fun `exclusions are order independent`() {
        val a = GroupCodec.encode(rules(excluded = setOf("one", "two")))
        val b = GroupCodec.encode(rules(excluded = setOf("two", "one")))
        assertEquals(a, b)
    }

    @Test
    fun `surrounding whitespace from a paste is tolerated`() {
        val encoded = "  ${GroupCodec.encode(rules())}\n"
        assertEquals(rules(), GroupCodec.decode(encoded))
    }

    @Test
    fun `malformed codes are rejected rather than guessed at`() {
        assertNull(GroupCodec.decode(""))
        assertNull(GroupCodec.decode("nonsense"))
        assertNull(GroupCodec.decode("TST9|A|B|1|14|UTC|TOP_APPS|0||FF")) // wrong version
        assertNull(GroupCodec.decode("TST1|A1|name|notanumber|14|UTC|TOP_APPS|0||FF"))
        assertNull(GroupCodec.decode("TST1|A1|name|1|14|UTC|NOT_A_LEVEL|0||FF"))
        assertNull(GroupCodec.decode("TST1|A1|name|1|14|UTC|TOP_APPS|0")) // truncated
    }

    @Test
    fun `absurd period lengths are rejected`() {
        assertNull(GroupCodec.decode("TST1|A1|name|1|0|UTC|TOP_APPS|0||FF"))
        assertNull(GroupCodec.decode("TST1|A1|name|1|9999|UTC|TOP_APPS|0||FF"))
    }
}
