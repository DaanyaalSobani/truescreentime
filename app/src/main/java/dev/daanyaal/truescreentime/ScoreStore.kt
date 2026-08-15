package dev.daanyaal.truescreentime

import android.content.Context
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import dev.daanyaal.truescreentime.ScorecardCrypto.fromHexOrNull
import dev.daanyaal.truescreentime.ScorecardCrypto.toHex

/**
 * Holds this device's signing key and everything it knows about a group's
 * scores. The key is generated once and never leaves the phone; only the
 * public half travels, inside signed blocks.
 */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    /** Bumped every time we publish, so newer cards win on merge. */
    var revision: Long
        get() = prefs.getLong(KEY_REVISION, 0L)
        set(value) = prefs.edit().putLong(KEY_REVISION, value).apply()

    fun keyPair(): KeyPair {
        val storedPrivate = prefs.getString(KEY_PRIVATE, null)?.fromHexOrNull()
        val storedPublic = prefs.getString(KEY_PUBLIC, null)?.fromHexOrNull()
        if (storedPrivate != null && storedPublic != null) {
            try {
                val factory = KeyFactory.getInstance("EC")
                return KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(storedPublic)),
                    factory.generatePrivate(PKCS8EncodedKeySpec(storedPrivate)),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Stored key unusable, generating a new one", e)
            }
        }
        return ScorecardCrypto.generateKeyPair().also { pair ->
            prefs.edit()
                .putString(KEY_PRIVATE, pair.private.encoded.toHex())
                .putString(KEY_PUBLIC, pair.public.encoded.toHex())
                .apply()
        }
    }

    /** Known blocks for a group, keyed by member id. */
    fun blocks(groupId: String): Map<String, MemberBlock> {
        val stored = prefs.getString(keyFor(groupId), null) ?: return emptyMap()
        val card = ScorecardCodec.decode(stored) ?: return emptyMap()
        return card.blocks.associateBy { it.memberId }
    }

    fun saveBlocks(rules: GroupRules, blocks: Collection<MemberBlock>) {
        val card = Scorecard(
            groupId = rules.groupId,
            periodStartDay = rules.periodStartDay,
            periodDays = rules.periodDays,
            blocks = blocks.toList(),
        )
        prefs.edit().putString(keyFor(rules.groupId), ScorecardCodec.encode(card)).apply()
    }

    fun clear(groupId: String) {
        prefs.edit().remove(keyFor(groupId)).apply()
    }

    private fun keyFor(groupId: String) = "blocks_$groupId"

    private companion object {
        const val TAG = "ScoreStore"
        const val KEY_REVISION = "revision"
        const val KEY_PRIVATE = "signing_private"
        const val KEY_PUBLIC = "signing_public"
    }
}
