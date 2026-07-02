package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pRevocationPolicyTest {

    @Test
    fun noActiveRequesterNeverMatches() {
        assertFalse(P2pRevocationPolicy.matchesActiveRequester(null, "+393331234567"))
    }

    @Test
    fun sameNumberMatches() {
        assertTrue(P2pRevocationPolicy.matchesActiveRequester("+393331234567", "+393331234567"))
    }

    @Test
    fun equivalentNationalAndInternationalFormsMatch() {
        assertTrue(P2pRevocationPolicy.matchesActiveRequester("+393331234567", "3331234567"))
    }

    @Test
    fun differentNumberDoesNotMatch() {
        assertFalse(P2pRevocationPolicy.matchesActiveRequester("+393331234567", "+393339999999"))
    }
}
