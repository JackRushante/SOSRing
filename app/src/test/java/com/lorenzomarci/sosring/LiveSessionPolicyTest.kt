package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionPolicyTest {

    @Test
    fun deadlineIsWallClockStartPlusDuration() {
        assertEquals(1_900_000L, LiveSessionPolicy.deadlineMs(1_000_000L, 15))
    }

    @Test
    fun sessionExpiresOnlyAtOrAfterDeadline() {
        val deadline = LiveSessionPolicy.deadlineMs(0L, 15)
        assertFalse(LiveSessionPolicy.isExpired(deadline, deadline - 1))
        assertTrue(LiveSessionPolicy.isExpired(deadline, deadline))
        assertTrue(LiveSessionPolicy.isExpired(deadline, deadline + 60_000L))
    }

    @Test
    fun staleThresholdIsThreeIntervalsWithFloor() {
        assertEquals(30_000L, LiveSessionPolicy.staleAfterMs(10_000L))
        // intervalli corti non devono far scattare falsi allarmi sotto i 30s
        assertEquals(30_000L, LiveSessionPolicy.staleAfterMs(5_000L))
        assertEquals(90_000L, LiveSessionPolicy.staleAfterMs(30_000L))
    }

    @Test
    fun staleOnlyAfterThresholdSinceLastUpdate() {
        assertFalse(LiveSessionPolicy.isStale(100_000L, 10_000L, 129_999L))
        assertTrue(LiveSessionPolicy.isStale(100_000L, 10_000L, 130_000L))
    }

    @Test
    fun neverStaleBeforeFirstUpdate() {
        // il caso "nessun primo punto" è gestito da LiveNoResponsePolicy / WAITING_FIRST
        assertFalse(LiveSessionPolicy.isStale(0L, 10_000L, 999_999L))
    }

    @Test
    fun watchdogTickIsShorterThanStaleWindow() {
        assertTrue(LiveSessionPolicy.WATCHDOG_TICK_MS < LiveSessionPolicy.MIN_STALE_MS)
    }
}
