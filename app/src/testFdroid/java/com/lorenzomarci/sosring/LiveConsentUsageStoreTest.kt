package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LiveConsentUsageStoreTest {

    @Test
    fun sessionCrossingMidnightKeysToStartDay() {
        val peerNumber = "+391234567890"
        val dayNStart = 1_000L * 24L * 60L * 60L
        val sessionStartMs = dayNStart + 23L * 60L * 60_000L + 30L * 60_000L
        val sessionStopMs = sessionStartMs + 60L * 60_000L

        val startDay = LiveConsentUsageStore.epochDay(sessionStartMs)
        val stopDay = LiveConsentUsageStore.epochDay(sessionStopMs)
        assertNotEquals(startDay, stopDay)

        val accrualKey = LiveConsentUsageStore.key(peerNumber, LiveConsentUsageStore.epochDay(sessionStartMs))
        assertEquals(LiveConsentUsageStore.key(peerNumber, startDay), accrualKey)
        assertNotEquals(LiveConsentUsageStore.key(peerNumber, stopDay), accrualKey)
    }

    @Test
    fun sessionWithinSameDayKeysToThatDay() {
        val peerNumber = "+391234567890"
        val sessionStartMs = 2_000L * 24L * 60L * 60L * 1000L + 10L * 60L * 60_000L
        val sessionStopMs = sessionStartMs + 30L * 60_000L

        assertEquals(LiveConsentUsageStore.epochDay(sessionStartMs), LiveConsentUsageStore.epochDay(sessionStopMs))
    }
}
