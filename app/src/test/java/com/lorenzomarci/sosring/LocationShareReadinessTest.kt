package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationShareReadinessTest {

    @Test
    fun missingPassphraseIsNotReportedAsServerFailure() {
        val result = LocationShareReadiness.check(
            ownPhoneNumber = "+391234567890",
            serverUrl = "https://push.example.com",
            encryptionConfigured = false
        )

        assertEquals(LocationShareBlockReason.MISSING_KEY, result)
    }

    @Test
    fun missingServerIsReportedBeforeKeyStatus() {
        val result = LocationShareReadiness.check(
            ownPhoneNumber = "+391234567890",
            serverUrl = "",
            encryptionConfigured = false
        )

        assertEquals(LocationShareBlockReason.MISSING_SERVER, result)
    }

    @Test
    fun configuredLocationSharingHasNoBlocker() {
        val result = LocationShareReadiness.check(
            ownPhoneNumber = "+391234567890",
            serverUrl = "https://push.example.com",
            encryptionConfigured = true
        )

        assertEquals(LocationShareBlockReason.NONE, result)
    }
}
