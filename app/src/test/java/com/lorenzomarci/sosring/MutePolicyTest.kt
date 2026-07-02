package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutePolicyTest {

    @Test
    fun notMutedWhenNeverSet() {
        assertFalse(MutePolicy.isMuted(untilMs = 0L, nowMs = 1_000L))
    }

    @Test
    fun mutedWhenNowBeforeUntil() {
        assertTrue(MutePolicy.isMuted(untilMs = 2_000L, nowMs = 1_000L))
    }

    @Test
    fun notMutedWhenNowAtUntil() {
        assertFalse(MutePolicy.isMuted(untilMs = 2_000L, nowMs = 2_000L))
    }

    @Test
    fun notMutedWhenNowAfterUntil() {
        assertFalse(MutePolicy.isMuted(untilMs = 2_000L, nowMs = 3_000L))
    }
}
