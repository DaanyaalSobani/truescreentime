package dev.daanyaal.truescreentime

import java.net.URLDecoder
import java.net.URLEncoder

/** How much each member agrees to reveal to the group. */
enum class SharingLevel { TOTAL_ONLY, TOP_APPS, FULL }

/**
 * The rules everyone in a group computes against, fixed when the group is
 * created and carried in the invite so every phone scores identically.
 *
 * Deliberately separate from personal filters: what you choose to hide from
 * your own list must not quietly change your competitive number.
 */
data class GroupRules(
    val groupId: String,
    val name: String,
    val periodStartDay: Long,
    val periodDays: Int,
    val timeZoneId: String,
    val excludedPackages: Set<String>,
    val countSystemApps: Boolean,
    val sharingLevel: SharingLevel,
    val secretHex: String,
) {
    val lastDayStart: Long
        get() = periodStartDay + (periodDays - 1) * DateRange.DAY_MS

    val endExclusive: Long
        get() = periodStartDay + periodDays * DateRange.DAY_MS
}

/**
 * Encodes group rules as a single line of text — the payload behind an
 * invite code or QR. Versioned so future formats can be rejected cleanly
 * rather than misread.
 */
object GroupCodec {

    private const val VERSION = "TST1"
    private const val FIELD = "|"
    private const val LIST = ","

    fun encode(rules: GroupRules): String = listOf(
        VERSION,
        rules.groupId,
        esc(rules.name),
        rules.periodStartDay.toString(),
        rules.periodDays.toString(),
        esc(rules.timeZoneId),
        rules.sharingLevel.name,
        if (rules.countSystemApps) "1" else "0",
        rules.excludedPackages.sorted().joinToString(LIST) { esc(it) },
        rules.secretHex,
    ).joinToString(FIELD)

    /** Returns null for anything malformed or from a newer format. */
    fun decode(text: String): GroupRules? {
        val parts = text.trim().split(FIELD)
        if (parts.size != 10 || parts[0] != VERSION) return null
        return try {
            GroupRules(
                groupId = parts[1].ifBlank { return null },
                name = unesc(parts[2]),
                periodStartDay = parts[3].toLong(),
                periodDays = parts[4].toInt().also { if (it !in 1..366) return null },
                timeZoneId = unesc(parts[5]),
                sharingLevel = SharingLevel.valueOf(parts[6]),
                countSystemApps = parts[7] == "1",
                excludedPackages = parts[8]
                    .split(LIST)
                    .filter { it.isNotBlank() }
                    .map { unesc(it) }
                    .toSet(),
                secretHex = parts[9],
            )
        } catch (e: IllegalArgumentException) {
            null // bad number, unknown sharing level
        }
    }

    private fun esc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun unesc(value: String): String = URLDecoder.decode(value, "UTF-8")
}
