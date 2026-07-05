package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRetryPolicyTest {

    @Test
    fun controlMessagesGetThreeAttempts() {
        assertEquals(3, ControlRetryPolicy.MAX_ATTEMPTS)
        assertEquals(ControlRetryPolicy.MAX_ATTEMPTS, ControlRetryPolicy.DELAYS_MS.size)
    }

    @Test
    fun firstAttemptIsImmediate() {
        assertEquals(0L, ControlRetryPolicy.delayBeforeAttempt(0))
    }

    @Test
    fun backoffGrowsBetweenAttempts() {
        for (i in 1 until ControlRetryPolicy.MAX_ATTEMPTS) {
            assertTrue(ControlRetryPolicy.delayBeforeAttempt(i) > ControlRetryPolicy.delayBeforeAttempt(i - 1))
        }
    }

    @Test
    fun attemptsBeyondTableReuseLastDelay() {
        assertEquals(
            ControlRetryPolicy.DELAYS_MS.last(),
            ControlRetryPolicy.delayBeforeAttempt(99)
        )
    }
}
