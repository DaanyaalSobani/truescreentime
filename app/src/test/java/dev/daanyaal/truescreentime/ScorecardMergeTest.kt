package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorecardMergeTest {

    private fun block(
        id: String,
        revision: Long = 1,
        minutes: Int = 100,
        signed: Boolean = false,
    ) = MemberBlock(
        memberId = id,
        displayName = id,
        revision = revision,
        dailyMinutes = listOf(minutes, minutes),
        publicKeyHex = if (signed) "AB".repeat(64) else "",
        signatureHex = if (signed) "CD".repeat(70) else "",
    )

    private fun card(vararg blocks: MemberBlock, groupId: String = "G1") = Scorecard(
        groupId = groupId,
        periodStartDay = 0,
        periodDays = 2,
        blocks = blocks.toList(),
    )

    @Test
    fun `unknown members are added`() {
        val result = ScorecardMerge.merge(emptyMap(), card(block("ana")), "G1")
        assertEquals(1, result.newMembers)
        assertEquals(0, result.updatedMembers)
        assertTrue(result.changed)
    }

    @Test
    fun `a newer revision replaces an older one`() {
        val known = mapOf("ana" to block("ana", revision = 1, minutes = 200))
        val result = ScorecardMerge.merge(known, card(block("ana", revision = 2, minutes = 90)), "G1")

        assertEquals(1, result.updatedMembers)
        assertEquals(2L, result.blocks.getValue("ana").revision)
        assertEquals(90 * 2 * 60_000L, result.blocks.getValue("ana").totalMs)
    }

    @Test
    fun `an older card cannot undo newer data`() {
        val known = mapOf("ana" to block("ana", revision = 5, minutes = 60))
        val result = ScorecardMerge.merge(known, card(block("ana", revision = 2, minutes = 999)), "G1")

        assertEquals(0, result.updatedMembers)
        assertEquals(5L, result.blocks.getValue("ana").revision)
    }

    @Test
    fun `importing the same card twice changes nothing`() {
        val incoming = card(block("ana", revision = 3))
        val once = ScorecardMerge.merge(emptyMap(), incoming, "G1")
        val twice = ScorecardMerge.merge(once.blocks, incoming, "G1")

        assertEquals(0, twice.newMembers)
        assertEquals(0, twice.updatedMembers)
        assertEquals(once.blocks, twice.blocks)
    }

    @Test
    fun `a signed block upgrades a relayed copy of the same revision`() {
        val known = mapOf("ana" to block("ana", revision = 4, signed = false))
        val result = ScorecardMerge.merge(
            known, card(block("ana", revision = 4, signed = true)), "G1"
        )

        assertEquals(1, result.updatedMembers)
        assertTrue(result.blocks.getValue("ana").isFirstHand)
    }

    @Test
    fun `a relayed copy never downgrades a verified block`() {
        val known = mapOf("ana" to block("ana", revision = 4, signed = true))
        val result = ScorecardMerge.merge(
            known, card(block("ana", revision = 4, signed = false)), "G1"
        )

        assertEquals(0, result.updatedMembers)
        assertTrue(result.blocks.getValue("ana").isFirstHand)
    }

    @Test
    fun `blocks claiming a signature that does not verify are dropped`() {
        val result = ScorecardMerge.merge(
            emptyMap(),
            card(block("mallory", signed = true)),
            "G1",
            verify = { false },
        )
        assertEquals(0, result.newMembers)
        assertTrue(result.blocks.isEmpty())
    }

    @Test
    fun `cards from another group are ignored entirely`() {
        val known = mapOf("ana" to block("ana"))
        val result = ScorecardMerge.merge(known, card(block("ben"), groupId = "OTHER"), "G1")

        assertEquals(known, result.blocks)
        assertEquals(0, result.newMembers)
    }

    @Test
    fun `gossip converges regardless of who syncs first`() {
        val ana = block("ana", minutes = 50)
        val ben = block("ben", minutes = 70)
        val cat = block("cat", minutes = 60)

        // Ana meets Ben, then Ben meets Cat, then Cat meets Ana.
        val anaKnows = ScorecardMerge.merge(mapOf("ana" to ana), card(ben), "G1").blocks
        val benKnows = ScorecardMerge.merge(mapOf("ben" to ben), card(cat), "G1").blocks
        val catKnows = ScorecardMerge.merge(
            mapOf("cat" to cat), card(*anaKnows.values.toTypedArray()), "G1"
        ).blocks

        // Everything eventually reaches everyone.
        val everyone = ScorecardMerge.merge(
            catKnows, card(*benKnows.values.toTypedArray()), "G1"
        ).blocks
        assertEquals(setOf("ana", "ben", "cat"), everyone.keys)
    }

    @Test
    fun `the leaderboard puts the lowest total first`() {
        val ordered = ScorecardMerge.leaderboard(
            listOf(block("ana", minutes = 90), block("ben", minutes = 30), block("cat", minutes = 60))
        )
        assertEquals(listOf("ben", "cat", "ana"), ordered.map { it.memberId })
    }

    @Test
    fun `members who have reported nothing sort last, not first`() {
        val silent = MemberBlock("zoe", "Zoe", 1, listOf(MemberBlock.UNKNOWN, MemberBlock.UNKNOWN))
        val ordered = ScorecardMerge.leaderboard(listOf(silent, block("ana", minutes = 90)))
        assertEquals(listOf("ana", "zoe"), ordered.map { it.memberId })
    }
}
