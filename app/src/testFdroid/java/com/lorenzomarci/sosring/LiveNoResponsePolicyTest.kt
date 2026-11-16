package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNoResponsePolicyTest {

    @Test
    fun notTimedOutBeforeThreshold() {
        assertFalse(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = false, nowMs = 59_000L))
    }

    @Test
    fun timedOutAfterThresholdWithNoPoint() {
        assertTrue(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = false, nowMs = 60_000L))
    }

    @Test
    fun neverTimedOutOncePointReceived() {
        assertFalse(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = true, nowMs = 999_999L))
    }

    @Test
    fun firstFixIsLateAfterThirtySeconds() {
        assertFalse(LiveNoResponsePolicy.firstFixLate(1_000L, nowMs = 30_999L))
        assertTrue(LiveNoResponsePolicy.firstFixLate(1_000L, nowMs = 31_000L))
        assertFalse(LiveNoResponsePolicy.firstFixLate(0L, nowMs = 99_000L))
    }

    @Test
    fun firstFixReportArrivesBeforeTheNoResponseTimeout() {
        assertTrue(
            LiveNoResponsePolicy.FIRST_FIX_LATE_MS + LiveSessionPolicy.WATCHDOG_TICK_MS <
                LiveNoResponsePolicy.NO_RESPONSE_MS
        )
    }
}
