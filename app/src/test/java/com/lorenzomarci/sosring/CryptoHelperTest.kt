package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoHelperTest {

    private val passphraseA = PassphraseHelper.generate()
    private val passphraseB = PassphraseHelper.generate()
    private val myNumber = "+393932077480"
    private val theirNumber = "+393515713262"

    @Test
    fun encryptDecryptRoundtripWithSamePassphrase() {
        val plaintext = """{"lat":37.5,"lon":15.1,"acc":12.0}"""
        val encrypted = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, passphraseA)
        val decrypted = CryptoHelper.decryptWithPassphrase(encrypted, myNumber, theirNumber, passphraseA)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun differentPassphrasesProduceDifferentCiphertext() {
        val plaintext = "test-location"
        val encA = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, passphraseA)
        val encB = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, passphraseB)
        assertNotEquals(encA, encB)
    }

    @Test
    fun decryptWithWrongPassphraseReturnsNullOrDifferent() {
        val plaintext = "secret"
        val encrypted = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, passphraseA)
        val decrypted = CryptoHelper.decryptWithPassphrase(encrypted, myNumber, theirNumber, passphraseB)
        assertNull(decrypted)
    }

    @Test
    fun keyDerivationIsSymmetricForPair() {
        val plaintext = "pair-test"
        val encrypted = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, passphraseA)
        val decryptedFromOtherSide = CryptoHelper.decryptWithPassphrase(encrypted, theirNumber, myNumber, passphraseA)
        assertEquals(plaintext, decryptedFromOtherSide)
    }

    @Test
    fun emptyPassphraseStillEncrypts() {
        val plaintext = "no-pass"
        val encrypted = CryptoHelper.encryptWithPassphrase(plaintext, myNumber, theirNumber, "")
        val decrypted = CryptoHelper.decryptWithPassphrase(encrypted, myNumber, theirNumber, "")
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun corruptedCiphertextReturnsNull() {
        val encrypted = CryptoHelper.encryptWithPassphrase("data", myNumber, theirNumber, passphraseA)
        val corrupted = encrypted.dropLast(4) + "AAAA"
        val decrypted = CryptoHelper.decryptWithPassphrase(corrupted, myNumber, theirNumber, passphraseA)
        assertNull(decrypted)
    }
}
