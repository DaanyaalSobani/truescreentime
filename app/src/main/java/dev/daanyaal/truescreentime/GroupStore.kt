package dev.daanyaal.truescreentime

import android.content.Context
import java.security.SecureRandom

/**
 * Remembers the group this device belongs to, plus the pseudonymous
 * identity it reports under. No account, no email — just a random id
 * generated on first use and a display name the user picks.
 */
class GroupStore(context: Context) {

    private val prefs = context.getSharedPreferences("groups", Context.MODE_PRIVATE)

    /** Stable per-install id. Created lazily, never leaves with real identity. */
    val memberId: String
        get() = prefs.getString(KEY_MEMBER_ID, null) ?: randomHex(8).also {
            prefs.edit().putString(KEY_MEMBER_ID, it).apply()
        }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    var activeGroup: GroupRules?
        get() = prefs.getString(KEY_GROUP, null)?.let { GroupCodec.decode(it) }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_GROUP)
            else editor.putString(KEY_GROUP, GroupCodec.encode(value))
            editor.apply()
        }

    fun leave() {
        activeGroup = null
    }

    companion object {
        private const val KEY_GROUP = "active_group"
        private const val KEY_MEMBER_ID = "member_id"
        private const val KEY_DISPLAY_NAME = "display_name"

        private val random = SecureRandom()

        fun randomHex(bytes: Int): String {
            val buffer = ByteArray(bytes)
            random.nextBytes(buffer)
            return buffer.joinToString("") { "%02X".format(it) }
        }
    }
}
