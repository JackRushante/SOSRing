package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNoResponsePolicyTest {

    @Test
    fun notTimedOutBeforeThreshold() {
        assertFalse(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = false, nowMs = 39_000L))
    }

    @Test
    fun timedOutAfterThresholdWithNoPoint() {
        assertTrue(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = false, nowMs = 40_000L))
    }

    @Test
    fun neverTimedOutOncePointReceived() {
        assertFalse(LiveNoResponsePolicy.timedOut(0L, firstPointReceived = true, nowMs = 999_999L))
    }
}
