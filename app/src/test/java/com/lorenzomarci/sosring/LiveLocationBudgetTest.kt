package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveLocationBudgetTest {

    @Test
    fun coversFullDuration() {
        assertEquals(360, LiveLocationBudget.maxUpdates(60 * 60_000L, 10_000L))
        assertEquals(5, LiveLocationBudget.maxUpdates(50_000L, 10_000L))
    }

    @Test
    fun roundsUpPartialInterval() {
        assertEquals(2, LiveLocationBudget.maxUpdates(15_000L, 10_000L))
        assertEquals(1, LiveLocationBudget.maxUpdates(10_000L, 10_000L))
    }

    @Test
    fun neverZeroSoTheRequestAlwaysSelfTerminates() {
        assertEquals(1, LiveLocationBudget.maxUpdates(0L, 10_000L))
    }

    @Test
    fun guardsAgainstZeroInterval() {
        assertEquals(1, LiveLocationBudget.maxUpdates(60_000L, 0L))
    }

    @Test
    fun fdroidDefaultFifteenMinutes() {
        assertEquals(90, LiveLocationBudget.maxUpdates(15 * 60_000L, 10_000L))
    }
}
