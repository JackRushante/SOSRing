package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConsentBudgetTest {

    @Test
    fun allowsWhenUnderCap() {
        assertTrue(LiveConsentBudget.allow(usedMsToday = 1_000L, requestedMs = 2_000L, dailyCapMs = 10_000L))
    }

    @Test
    fun rejectsWhenOverCap() {
        assertFalse(LiveConsentBudget.allow(usedMsToday = 9_000L, requestedMs = 2_000L, dailyCapMs = 10_000L))
    }

    @Test
    fun allowsExactlyAtCapBoundary() {
        assertTrue(LiveConsentBudget.allow(usedMsToday = 8_000L, requestedMs = 2_000L, dailyCapMs = 10_000L))
    }
}
