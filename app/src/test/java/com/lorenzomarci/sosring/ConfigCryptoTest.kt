package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCryptoTest {

    private val payload = """{"version":1,"passphrase":"abc","ntfyAuthToken":"tk_xyz"}"""
    private val password = "correct horse battery staple"

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
