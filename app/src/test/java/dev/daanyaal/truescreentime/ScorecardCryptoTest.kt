package dev.daanyaal.truescreentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorecardCryptoTest {

    private val keys = ScorecardCrypto.generateKeyPair()
    private val publicHex = ScorecardCrypto.encodePublicKey(keys.public)

    @Test
    fun `a signature made with the private key verifies with the public one`() {
        val signature = ScorecardCrypto.sign("payload", keys.private)
        assertTrue(ScorecardCrypto.verify("payload", signature, publicHex))
    }

    @Test
    fun `editing the payload invalidates the signature`() {
        // This is the property that stops a relayer changing someone's score.
        val signature = ScorecardCrypto.sign("ana 120 95", keys.private)
        assertFalse(ScorecardCrypto.verify("ana 999 95", signature, publicHex))
    }

    @Test
    fun `another members key cannot vouch for this payload`() {
        val other = ScorecardCrypto.generateKeyPair()
        val signature = ScorecardCrypto.sign("payload", keys.private)
        assertFalse(
            ScorecardCrypto.verify(
                "payload", signature, ScorecardCrypto.encodePublicKey(other.public)
            )
        )
    }

    @Test
    fun `public keys are a fixed 64 bytes and round trip`() {
        assertEquals(128, publicHex.length) // 64 bytes as hex
        val decoded = ScorecardCrypto.decodePublicKey(publicHex)
        assertNotNull(decoded)
        assertEquals(publicHex, ScorecardCrypto.encodePublicKey(decoded!!))
    }

    @Test
    fun `garbage keys and signatures are rejected without throwing`() {
        assertNull(ScorecardCrypto.decodePublicKey("not hex"))
        assertNull(ScorecardCrypto.decodePublicKey("AABB")) // right form, wrong length
        assertNull(ScorecardCrypto.decodePublicKey("FF".repeat(64))) // not on the curve
        assertFalse(ScorecardCrypto.verify("payload", "zzzz", publicHex))
        assertFalse(ScorecardCrypto.verify("payload", "AABB", publicHex))
    }

    @Test
    fun `a signed member block verifies end to end`() {
        val block = MemberBlock(
            memberId = "AA11",
            displayName = "Ana",
            revision = 2,
            dailyMinutes = listOf(120, 95),
        )
        val payload = block.signingPayload("G1")
        val signed = block.copy(
            publicKeyHex = publicHex,
            signatureHex = ScorecardCrypto.sign(payload, keys.private),
        )

        assertTrue(
            ScorecardCrypto.verify(
                signed.signingPayload("G1"), signed.signatureHex, signed.publicKeyHex
            )
        )
        // The same block replayed into a different group must not verify.
        assertFalse(
            ScorecardCrypto.verify(
                signed.signingPayload("G2"), signed.signatureHex, signed.publicKeyHex
            )
        )
    }
}
