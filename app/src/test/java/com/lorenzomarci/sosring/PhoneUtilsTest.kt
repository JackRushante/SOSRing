package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {

    @Test
    fun normalizeStripsSeparators() {
        assertEquals("+39393207780", PhoneUtils.normalize("+39 393 207 780"))
        assertEquals("+39393207780", PhoneUtils.normalize("+39-393-207-780"))
        assertEquals("+39393207780", PhoneUtils.normalize("+39(393)207.780"))
    }

    @Test
    fun normalizeConvertsDoubleZeroToPlus() {
        assertEquals("+39393207780", PhoneUtils.normalize("0039393207780"))
        assertEquals("+12125551234", PhoneUtils.normalize("0012125551234"))
    }

    @Test
    fun normalizeItalianLongNumberWithoutPrefix() {
        assertEquals("+393932077480", PhoneUtils.normalize("393932077480"))
    }

    @Test
    fun normalizeItalianLocalMobile() {
        // Italian mobile: 3XX XXX XXXX = 10 digits without prefix → +39 prefixed
        assertEquals("+393932077480", PhoneUtils.normalize("3932077480"))
        assertEquals("+393201234567", PhoneUtils.normalize("3201234567"))
    }

    @Test
    fun normalizePreservesAlreadyInternational() {
        assertEquals("+39393207780", PhoneUtils.normalize("+39393207780"))
        assertEquals("+12125551234", PhoneUtils.normalize("+12125551234"))
    }

    @Test
    fun normalizeDoesNotPrefixShortItalianNumber() {
        assertEquals("3934", PhoneUtils.normalize("3934"))
        assertEquals("39345", PhoneUtils.normalize("39345"))
        assertEquals("393456", PhoneUtils.normalize("393456"))
    }

    @Test
    fun normalizeIsIdempotent() {
        val input = "+39393207780"
        assertEquals(input, PhoneUtils.normalize(PhoneUtils.normalize(input)))
    }

    @Test
    fun normalizeHandlesEmptyAndWhitespace() {
        assertEquals("", PhoneUtils.normalize(""))
        assertEquals("+39", PhoneUtils.normalize("   +39   "))
    }

    @Test
    fun matches_sameNormalizedNumber_returnsTrue() {
        assertTrue(PhoneUtils.matches("3401234567", "+39 340 123 4567"))
    }

    @Test
    fun matches_differentNumbersSameSuffix_returnsFalse() {
        assertFalse(PhoneUtils.matches("+393401234567", "+393391234567"))
    }

    @Test
    fun matches_blankInput_returnsFalse() {
        assertFalse(PhoneUtils.matches("", "+393401234567"))
        assertFalse(PhoneUtils.matches("   ", "+393401234567"))
    }
}
