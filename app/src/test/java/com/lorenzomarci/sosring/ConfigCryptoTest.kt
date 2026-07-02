package com.lorenzomarci.sosring

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ConfigCryptoTest {

    private val payload = """{"version":1,"passphrase":"abc","ntfyAuthToken":"tk_xyz"}"""
    private val password = "correct horse battery staple"

    private fun buildEnvelopeWithIterations(plaintext: String, password: String, iterations: Int): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return JSONObject().apply {
            put("format", "sosring-enc-v1")
            put("iter", iterations)
            put("salt", enc.encodeToString(salt))
            put("iv", enc.encodeToString(iv))
            put("data", enc.encodeToString(ciphertext))
        }.toString()
    }

    @Test
    fun decryptRejectsIterationsBelowMinimum() {
        val envelope = buildEnvelopeWithIterations(payload, password, 1)
        assertNull(ConfigCrypto.decrypt(envelope, password))
    }

    @Test
    fun decryptRejectsIterationsAboveMaximum() {
        val envelope = buildEnvelopeWithIterations(payload, password, 1_100_000)
        assertNull(ConfigCrypto.decrypt(envelope, password))
    }

    @Test
    fun encryptThenDecryptRoundtrips() {
        val envelope = ConfigCrypto.encrypt(payload, password)
        val decrypted = ConfigCrypto.decrypt(envelope, password)
        assertEquals(payload, decrypted)
    }

    @Test
    fun decryptWithWrongPasswordReturnsNull() {
        val envelope = ConfigCrypto.encrypt(payload, password)
        assertNull(ConfigCrypto.decrypt(envelope, "wrong password"))
    }

    @Test
    fun tamperedCiphertextReturnsNull() {
        val envelope = ConfigCrypto.encrypt(payload, password)
        val tampered = envelope.dropLast(8) + "AAAAAAAA"
        assertNull(ConfigCrypto.decrypt(tampered, password))
    }

    @Test
    fun decryptOfNonEnvelopeReturnsNull() {
        assertNull(ConfigCrypto.decrypt("""{"version":1}""", password))
        assertNull(ConfigCrypto.decrypt("not even json", password))
    }

    @Test
    fun isEncryptedEnvelopeTrueForOwnOutput() {
        assertTrue(ConfigCrypto.isEncryptedEnvelope(ConfigCrypto.encrypt(payload, password)))
    }

    @Test
    fun isEncryptedEnvelopeFalseForPlainConfigJson() {
        assertFalse(ConfigCrypto.isEncryptedEnvelope("""{"version":1,"passphrase":"abc"}"""))
        assertFalse(ConfigCrypto.isEncryptedEnvelope("garbage"))
    }

    @Test
    fun sameInputProducesDifferentEnvelopes() {
        val a = ConfigCrypto.encrypt(payload, password)
        val b = ConfigCrypto.encrypt(payload, password)
        assertNotEquals(a, b)
        assertEquals(payload, ConfigCrypto.decrypt(a, password))
        assertEquals(payload, ConfigCrypto.decrypt(b, password))
    }
}
