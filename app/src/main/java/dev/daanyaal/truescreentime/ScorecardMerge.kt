package dev.daanyaal.truescreentime

/**
 * Merges scorecards from other phones into what this one already knows.
 *
 * The rules make sharing safe in any order, any number of times:
 *  - a higher revision from the same member wins;
 *  - at equal revisions, a first-hand (signed) block beats a relayed copy;
 *  - unsigned blocks never overwrite verified ones from the same revision;
 *  - importing the same card twice changes nothing.
 */
object ScorecardMerge {

    data class Result(
        val blocks: Map<String, MemberBlock>,
        val newMembers: Int,
        val updatedMembers: Int,
    ) {
        val changed: Boolean get() = newMembers > 0 || updatedMembers > 0
    }

    /**
     * [verify] decides whether a first-hand block's signature is good;
     * blocks that claim to be signed but fail are dropped entirely rather
     * than trusted as relayed data.
     */
    fun merge(
        known: Map<String, MemberBlock>,
        incoming: Scorecard,
        groupId: String,
        verify: (MemberBlock) -> Boolean = { true },
    ): Result {
        if (incoming.groupId != groupId) {
            return Result(known, newMembers = 0, updatedMembers = 0)
        }

        val merged = known.toMutableMap()
        var new = 0
        var updated = 0

        for (block in incoming.blocks) {
            if (block.isFirstHand && !verify(block)) continue

            val existing = merged[block.memberId]
            when {
                existing == null -> {
                    merged[block.memberId] = block
                    new++
                }
                block.revision > existing.revision -> {
                    merged[block.memberId] = block
                    updated++
                }
                block.revision == existing.revision &&
                    block.isFirstHand && !existing.isFirstHand -> {
                    // Same numbers, but now we can prove who said them.
                    merged[block.memberId] = block
                    updated++
                }
                else -> Unit // older or equal: keep what we have
            }
        }
        return Result(merged, new, updated)
    }

    /** Lowest total wins; members who have reported nothing sort last. */
    fun leaderboard(blocks: Collection<MemberBlock>): List<MemberBlock> =
        blocks.sortedWith(
            compareBy({ it.reportedDays == 0 }, { it.totalMs })
        )
}
