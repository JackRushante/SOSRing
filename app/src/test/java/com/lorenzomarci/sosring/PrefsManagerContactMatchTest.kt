package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsManagerContactMatchTest {

    @Test
    fun suffixMatchingWriteNumberPersistsLocationEnabled() {
        val contacts = listOf(VipContact(name = "Mamma", number = "+390461234567", locationEnabled = false))

        val updated = PrefsManager.applyLocationEnabledUpdate(contacts, "0461234567", true)

        assertTrue(updated.single().locationEnabled)
    }

    @Test
    fun nonMatchingWriteNumberLeavesContactsUnchanged() {
        val contacts = listOf(VipContact(name = "Mamma", number = "+390461234567", locationEnabled = true))

        val updated = PrefsManager.applyLocationEnabledUpdate(contacts, "+393391234567", false)

        assertTrue(updated.single().locationEnabled)
    }

    @Test
    fun exactStoredNumberEchoStillMatches() {
        val contacts = listOf(VipContact(name = "Papa", number = "+393932077480", locationEnabled = false))

        val updated = PrefsManager.applyLocationEnabledUpdate(contacts, "+393932077480", true)

        assertTrue(updated.single().locationEnabled)
    }

    @Test
    fun disablingUsesSameSuffixAwareMatcher() {
        val contacts = listOf(VipContact(name = "Mamma", number = "+390461234567", locationEnabled = true))

        val updated = PrefsManager.applyLocationEnabledUpdate(contacts, "0461234567", false)

        assertFalse(updated.single().locationEnabled)
    }
}
