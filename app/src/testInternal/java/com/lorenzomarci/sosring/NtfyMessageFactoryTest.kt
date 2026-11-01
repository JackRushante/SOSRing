package com.lorenzomarci.sosring

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyMessageFactoryTest {

    @Test
    fun keyRotatedMessageNeverContainsPassphrase() {
        val message = NtfyMessageFactory.keyRotated(
            fromHash = "sosring-abc",
            keyFingerprint = "deadbeef"
        )

        assertEquals("key_rotated", message.getString("type"))
        assertEquals("sosring-abc", message.getString("from"))
        assertEquals("deadbeef", message.getString("key_fp"))
        assertFalse(message.has("passphrase"))
        assertFalse(message.toString().contains("secret-key"))
    }

    @Test
    fun locationErrorCarriesReasonAndVersionsButNoPosition() {
        val message = NtfyMessageFactory.locationError(
            fromHash = "sosring-me",
            reason = LocationUnavailableReason.LOCATION_OFF.code,
            versionCode = 64,
            sdkInt = 36
        )

        assertEquals("loc_error", message.getString("type"))
        assertEquals("sosring-me", message.getString("from"))
        assertEquals("location_off", message.getString("reason"))
        assertEquals(64, message.getInt("vc"))
        assertEquals(36, message.getInt("sdk"))
        assertTrue(message.has("ts"))
        assertFalse(message.has("lat"))
        assertFalse(message.has("enc"))
    }

    @Test
    fun liveStartCarriesSessionAndDurationOnly() {
        val message = NtfyMessageFactory.liveStart(
            fromHash = "sosring-me",
            sessionId = "session-1",
            durationMinutes = 15,
            intervalSeconds = 10
        )

        assertEquals("live_start", message.getString("type"))
        assertEquals("session-1", message.getString("session_id"))
        assertEquals(15, message.getInt("duration_min"))
        assertEquals(10, message.getInt("interval_sec"))
        assertFalse(message.has("lat"))
        assertFalse(message.has("lon"))
    }

    @Test
    fun livePointUsesEncryptedPayload() {
        val encrypted = "encrypted-body"
        val message = NtfyMessageFactory.livePoint(
            fromHash = "sosring-me",
            encryptedPayload = encrypted
        )

        assertEquals("live_point", message.getString("type"))
        assertEquals(encrypted, message.getString("enc"))
        assertFalse(message.has("lat"))
        assertFalse(message.has("lon"))
        assertTrue(JSONObject(message.toString()).has("ts"))
    }
}
