package dev.daanyaal.truescreentime

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * Signs and verifies member blocks so a relayed scorecard cannot be edited
 * in transit by whoever passed it along.
 *
 * Uses ECDSA on P-256 rather than Ed25519: Ed25519 only reached the Android
 * platform APIs in 33, and this app still supports 24. P-256 has been there
 * since the beginning and needs no extra dependency.
 */
object ScorecardCrypto {

    private const val CURVE = "secp256r1"
    private const val KEY_ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val COORDINATE_BYTES = 32

    // BigInteger.TWO is Java 9+; spell them out for the minSdk we support.
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val THREE: BigInteger = BigInteger.valueOf(3)

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
        initialize(ECGenParameterSpec(CURVE))
    }.generateKeyPair()

    fun sign(payload: String, privateKey: PrivateKey): String =
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
            sign().toHex()
        }

    /** False for a bad signature, a wrong key, or malformed input. */
    fun verify(payload: String, signatureHex: String, publicKeyHex: String): Boolean {
        val publicKey = decodePublicKey(publicKeyHex) ?: return false
        val signature = signatureHex.fromHexOrNull() ?: return false
        return try {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(payload.toByteArray(Charsets.UTF_8))
                verify(signature)
            }
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    /** Raw X||Y, 64 bytes — smaller than the DER encoding, and fixed size. */
    fun encodePublicKey(publicKey: PublicKey): String {
        val point = (publicKey as ECPublicKey).w
        return point.affineX.toFixedBytes().toHex() + point.affineY.toFixedBytes().toHex()
    }

    fun decodePublicKey(hex: String): PublicKey? {
        val bytes = hex.fromHexOrNull() ?: return null
        if (bytes.size != COORDINATE_BYTES * 2) return null
        return try {
            val x = BigInteger(1, bytes.copyOfRange(0, COORDINATE_BYTES))
            val y = BigInteger(1, bytes.copyOfRange(COORDINATE_BYTES, bytes.size))
            val parameters = curveParameters()
            // KeyFactory happily accepts coordinates that are not on the
            // curve, so check the curve equation ourselves before trusting
            // anything signed against this key.
            if (!isOnCurve(x, y, parameters)) return null
            KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters))
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** y² ≡ x³ + ax + b (mod p), with both coordinates inside the field. */
    private fun isOnCurve(x: BigInteger, y: BigInteger, parameters: ECParameterSpec): Boolean {
        val curve = parameters.curve
        val field = curve.field as? ECFieldFp ?: return false
        val p = field.p
        if (x.signum() < 0 || x >= p || y.signum() < 0 || y >= p) return false

        val left = y.modPow(TWO, p)
        val right = x.modPow(THREE, p)
            .add(curve.a.multiply(x))
            .add(curve.b)
            .mod(p)
        return left == right
    }

    private fun curveParameters(): ECParameterSpec =
        AlgorithmParameters.getInstance(KEY_ALGORITHM).run {
            init(ECGenParameterSpec(CURVE))
            getParameterSpec(ECParameterSpec::class.java)
        }

    private fun BigInteger.toFixedBytes(): ByteArray {
        val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(COORDINATE_BYTES).also { padded ->
            raw.copyInto(padded, COORDINATE_BYTES - raw.size)
        }
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    fun String.fromHexOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return try {
            ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
