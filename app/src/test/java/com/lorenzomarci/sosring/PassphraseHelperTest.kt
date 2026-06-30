package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseHelperTest {

    @Test
    fun generateProducesValidPassphrase() {
        val passphrase = PassphraseHelper.generate()
        assertTrue(PassphraseHelper.isValid(passphrase))
        assertEquals(44, passphrase.length)
    }

    @Test
    fun generateProducesDifferentPassphrases() {
        val a = PassphraseHelper.generate()
        val b = PassphraseHelper.generate()
        assertNotEquals(a, b)
    }

    @Test
    fun fingerprintIsDeterministic() {
        val passphrase = PassphraseHelper.generate()
        val fp1 = PassphraseHelper.fingerprint(passphrase)
        val fp2 = PassphraseHelper.fingerprint(passphrase)
        assertEquals(fp1, fp2)
        assertEquals(8, fp1.length)
    }

    @Test
    fun fingerprintIsDifferentForDifferentPassphrases() {
        val fp1 = PassphraseHelper.fingerprint(PassphraseHelper.generate())
        val fp2 = PassphraseHelper.fingerprint(PassphraseHelper.generate())
        assertNotEquals(fp1, fp2)
    }

    @Test
    fun isValidRejectsInvalid() {
        assertFalse(PassphraseHelper.isValid(""))
        assertFalse(PassphraseHelper.isValid("not-base64!!!"))
        assertFalse(PassphraseHelper.isValid("aGVsbG8="))
        assertFalse(PassphraseHelper.isValid("short"))
    }
}
