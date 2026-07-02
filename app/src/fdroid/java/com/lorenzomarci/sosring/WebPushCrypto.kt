package com.lorenzomarci.sosring

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WebPushCrypto {

    private const val CURVE = "secp256r1"
    private const val DEFAULT_RECORD_SIZE = 4096

    fun encrypt(plaintext: ByteArray, p256dhBase64: String, authBase64: String): ByteArray {
        val uaPublic = b64dec(p256dhBase64)
        val auth = b64dec(authBase64)
        val ephemeral = generateKeyPair()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return encryptInternal(plaintext, uaPublic, auth, ephemeral, salt, DEFAULT_RECORD_SIZE)
    }

    internal fun encryptInternal(
        plaintext: ByteArray,
        uaPublic: ByteArray,
        auth: ByteArray,
        ephemeral: KeyPair,
        salt: ByteArray,
        recordSize: Int
    ): ByteArray {
        val asPublic = pointBytes(ephemeral.public as ECPublicKey)
        val shared = ecdh(ephemeral.private as ECPrivateKey, loadPublic(uaPublic))
        val keys = deriveKeys(shared, auth, uaPublic, asPublic, salt)
        val record = plaintext + byteArrayOf(0x02)
        val ciphertext = aesGcmSeal(keys.cek, keys.nonce, record)
        val header = salt + uint32(recordSize) + byteArrayOf(asPublic.size.toByte()) + asPublic
        return header + ciphertext
    }

    internal data class DerivedKeys(
        val prkKey: ByteArray,
        val ikm: ByteArray,
        val prk: ByteArray,
        val cek: ByteArray,
        val nonce: ByteArray
    )

    internal fun deriveKeys(
        ecdhSecret: ByteArray,
        auth: ByteArray,
        uaPublic: ByteArray,
        asPublic: ByteArray,
        salt: ByteArray
    ): DerivedKeys {
        val prkKey = hmac(auth, ecdhSecret)
        val keyInfo = "WebPush: info".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00) + uaPublic + asPublic
        val ikm = hkdfExpand(prkKey, keyInfo, 32)
        val prk = hmac(salt, ikm)
        val cek = hkdfExpand(prk, "Content-Encoding: aes128gcm".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00), 16)
        val nonce = hkdfExpand(prk, "Content-Encoding: nonce".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00), 12)
        return DerivedKeys(prkKey, ikm, prk, cek, nonce)
    }

    internal fun ecdh(privateKey: ECPrivateKey, publicKey: ECPublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    internal fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE))
        return generator.generateKeyPair()
    }

    internal fun loadPublic(point: ByteArray): ECPublicKey {
        require(point.size == 65 && point[0].toInt() == 0x04) { "invalid EC public point" }
        val x = BigInteger(1, point.copyOfRange(1, 33))
        val y = BigInteger(1, point.copyOfRange(33, 65))
        val params = ecParameters()
        require(isOnCurve(x, y, params)) { "EC public point is not on curve" }
        val spec = ECPublicKeySpec(ECPoint(x, y), params)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    private fun isOnCurve(x: BigInteger, y: BigInteger, params: ECParameterSpec): Boolean {
        val field = params.curve.field as ECFieldFp
        val p = field.p
        val a = params.curve.a
        val b = params.curve.b
        val lhs = y.modPow(BigInteger.valueOf(2), p)
        val rhs = x.modPow(BigInteger.valueOf(3), p).add(a.multiply(x)).add(b).mod(p)
        return lhs == rhs
    }

    internal fun loadPrivate(scalar: ByteArray): ECPrivateKey {
        val spec = ECPrivateKeySpec(BigInteger(1, scalar), ecParameters())
        return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
    }

    internal fun pointBytes(publicKey: ECPublicKey): ByteArray {
        val point = publicKey.w
        return byteArrayOf(0x04) + toFixed(point.affineX, 32) + toFixed(point.affineY, 32)
    }

    fun b64enc(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun b64dec(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun ecParameters(): ECParameterSpec {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec(CURVE))
        return params.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 32) { "hkdfExpand supports a single HMAC block (max 32 bytes)" }
        return hmac(prk, info + byteArrayOf(0x01)).copyOf(length)
    }

    private fun aesGcmSeal(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(data)
    }

    private fun uint32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun toFixed(value: BigInteger, length: Int): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == length -> raw
            raw.size == length + 1 && raw[0].toInt() == 0 -> raw.copyOfRange(1, raw.size)
            raw.size < length -> ByteArray(length - raw.size) + raw
            else -> raw.copyOfRange(raw.size - length, raw.size)
        }
    }
}
