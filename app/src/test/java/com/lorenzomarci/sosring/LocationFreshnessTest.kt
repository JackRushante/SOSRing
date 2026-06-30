package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    @Test
    fun recentFix_isFresh() {
        assertTrue(LocationFreshness.isFresh(fixTimeMs = 1_000_000L, nowMs = 1_060_000L))
    }

    @Test
    fun oldFix_isStale() {
        assertFalse(LocationFreshness.isFresh(fixTimeMs = 1_000_000L, nowMs = 1_500_000L))
    }

    @Test
    fun zeroOrNegativeTimestamp_isStale() {
        assertFalse(LocationFreshness.isFresh(fixTimeMs = 0L, nowMs = 1_000_000L))
    }
}
