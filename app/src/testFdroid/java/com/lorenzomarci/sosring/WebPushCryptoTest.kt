package com.lorenzomarci.sosring

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Test

class WebPushCryptoTest {

    private val rfcAuth = "BTBZMqHH6r4Tts7J_aSIgg"
    private val rfcUaPublic = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val rfcUaPrivate = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"
    private val rfcAsPublic = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
    private val rfcAsPrivate = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"
    private val rfcSalt = "DGv6ra1nlYgDCS1FRnbzlw"

    @Test
    fun deriveKeys_matchesRfc8291Vectors() {
        val asPrivate = WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcAsPrivate))
        val uaPublic = WebPushCrypto.b64dec(rfcUaPublic)
        val shared = WebPushCrypto.ecdh(asPrivate, WebPushCrypto.loadPublic(uaPublic))

        val keys = WebPushCrypto.deriveKeys(
            ecdhSecret = shared,
            auth = WebPushCrypto.b64dec(rfcAuth),
            uaPublic = uaPublic,
            asPublic = WebPushCrypto.b64dec(rfcAsPublic),
            salt = WebPushCrypto.b64dec(rfcSalt)
        )

        assertEquals("Snr3JMxaHVDXHWJn5wdC52WjpCtd2EIEGBykDcZW32k", WebPushCrypto.b64enc(keys.prkKey))
        assertEquals("S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg", WebPushCrypto.b64enc(keys.ikm))
        assertEquals("09_eUZGrsvxChDCGRCdkLiDXrReGOEVeSCdCcPBSJSc", WebPushCrypto.b64enc(keys.prk))
        assertEquals("oIhVW04MRdy2XN9CiKLxTg", WebPushCrypto.b64enc(keys.cek))
        assertEquals("4h_95klXJ5E_qnoN", WebPushCrypto.b64enc(keys.nonce))
    }

    @Test
    fun encrypt_matchesRfc8291Ciphertext() {
        val plaintext = "When I grow up, I want to be a watermelon".toByteArray(Charsets.UTF_8)
        val uaPublic = WebPushCrypto.b64dec(rfcUaPublic)
        val ephemeral = KeyPair(
            WebPushCrypto.loadPublic(WebPushCrypto.b64dec(rfcAsPublic)),
            WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcAsPrivate))
        )
        val salt = WebPushCrypto.b64dec(rfcSalt)

        val body = WebPushCrypto.encryptInternal(plaintext, uaPublic, WebPushCrypto.b64dec(rfcAuth), ephemeral, salt, 4096)

        assertArrayEquals(salt, body.copyOfRange(0, 16))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x10, 0x00), body.copyOfRange(16, 20))
        assertEquals(65, body[20].toInt() and 0xff)
        assertArrayEquals(WebPushCrypto.b64dec(rfcAsPublic), body.copyOfRange(21, 86))

        val ciphertext = body.copyOfRange(86, body.size)
        assertEquals(
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ",
            WebPushCrypto.b64enc(ciphertext)
        )
    }

    @Test
    fun roundTrip_randomEphemeralAndSalt() {
        val receiver = WebPushCrypto.generateKeyPair()
        val receiverPublic = WebPushCrypto.pointBytes(receiver.public as ECPublicKey)
        val auth = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val message = "loc_request from peer".toByteArray(Charsets.UTF_8)

        val body = WebPushCrypto.encrypt(message, WebPushCrypto.b64enc(receiverPublic), WebPushCrypto.b64enc(auth))

        val decrypted = decrypt(body, receiver.private as ECPrivateKey, receiverPublic, auth)
        assertArrayEquals(message, decrypted)
    }

    @Test
    fun loadPublic_acceptsOnCurvePoint() {
        val point = WebPushCrypto.b64dec(rfcUaPublic)

        val key = WebPushCrypto.loadPublic(point)

        assertArrayEquals(point, WebPushCrypto.pointBytes(key))
    }

    @Test
    fun loadPublic_rejectsOffCurvePoint() {
        val point = WebPushCrypto.b64dec(rfcUaPublic)
        val offCurve = point.copyOf()
        offCurve[64] = (offCurve[64].toInt() xor 0x01).toByte()

        try {
            WebPushCrypto.loadPublic(offCurve)
            fail("expected off-curve point to be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    private fun decrypt(body: ByteArray, receiverPrivate: ECPrivateKey, receiverPublic: ByteArray, auth: ByteArray): ByteArray {
        val salt = body.copyOfRange(0, 16)
        val idLen = body[20].toInt() and 0xff
        val keyId = body.copyOfRange(21, 21 + idLen)
        val ciphertext = body.copyOfRange(21 + idLen, body.size)

        val shared = WebPushCrypto.ecdh(receiverPrivate, WebPushCrypto.loadPublic(keyId))
        val keys = WebPushCrypto.deriveKeys(shared, auth, receiverPublic, keyId, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.cek, "AES"), GCMParameterSpec(128, keys.nonce))
        val record = cipher.doFinal(ciphertext)

        var end = record.size
        while (end > 0 && record[end - 1].toInt() == 0) end--
        return record.copyOfRange(0, end - 1)
    }
}
