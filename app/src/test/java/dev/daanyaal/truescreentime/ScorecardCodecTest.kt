package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorecardCodecTest {

    private fun block(
        id: String = "AA11BB22",
        name: String = "Ana",
        revision: Long = 3,
        daily: List<Int> = listOf(120, 95, MemberBlock.UNKNOWN, 60),
        signed: Boolean = true,
    ) = MemberBlock(
        memberId = id,
        displayName = name,
        revision = revision,
        dailyMinutes = daily,
        publicKeyHex = if (signed) "AB".repeat(64) else "",
        signatureHex = if (signed) "CD".repeat(70) else "",
    )

    private fun card(vararg blocks: MemberBlock) = Scorecard(
        groupId = "G1",
        periodStartDay = 1_800_000_000_000L,
        periodDays = 4,
        blocks = blocks.toList(),
    )

    @Test
    fun `a card survives a round trip`() {
        val original = card(block(), block(id = "CC33", name = "Ben", signed = false))
        assertEquals(original, ScorecardCodec.decode(ScorecardCodec.encode(original)))
    }

    @Test
    fun `names with separators and unicode survive`() {
        val original = card(block(name = "Ana | Bea ~ 🎯"))
        assertEquals(original, ScorecardCodec.decode(ScorecardCodec.encode(original)))
    }

    @Test
    fun `unknown days stay unknown rather than becoming zero`() {
        val decoded = ScorecardCodec.decode(ScorecardCodec.encode(card(block())))
        val daily = decoded!!.blocks.single().dailyMinutes
        assertEquals(MemberBlock.UNKNOWN, daily[2])
        assertEquals(3, decoded.blocks.single().reportedDays)
    }

    @Test
    fun `totals ignore unreported days`() {
        assertEquals((120 + 95 + 60) * 60_000L, block().totalMs)
    }

    @Test
    fun `first-hand blocks are distinguishable from relayed ones`() {
        assertTrue(block(signed = true).isFirstHand)
        assertFalse(block(signed = false).isFirstHand)
    }

    @Test
    fun `an empty card round trips`() {
        val original = card()
        assertEquals(original, ScorecardCodec.decode(ScorecardCodec.encode(original)))
    }

    @Test
    fun `malformed payloads are rejected`() {
        assertNull(ScorecardCodec.decode(""))
        assertNull(ScorecardCodec.decode("nonsense"))
        assertNull(ScorecardCodec.decode("TSC9|G1|0|4|")) // wrong version
        assertNull(ScorecardCodec.decode("TSC1|G1|notanumber|4|"))
    }

    @Test
    fun `a block whose days do not match the period is rejected`() {
        // Five values for a four-day period means the two sides disagree.
        val text = ScorecardCodec.encode(card(block(daily = listOf(1, 2, 3, 4, 5))))
        assertNull(ScorecardCodec.decode(text))
    }

    @Test
    fun `the signing payload changes whenever the numbers do`() {
        val a = block(daily = listOf(10, 20, 30, 40)).signingPayload("G1")
        val b = block(daily = listOf(10, 20, 30, 41)).signingPayload("G1")
        assertTrue(a != b)
        // ...and when the group changes, so a card cannot be replayed elsewhere.
        assertTrue(block().signingPayload("G1") != block().signingPayload("G2"))
    }
}
