package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMapStateFactoryTest {

    @Test
    fun emptySessionWaitsForLivePoints() {
        val state = LiveMapStateFactory.fromPoints(emptyList(), isLive = true, nowMs = 0L)

        assertFalse(state.hasPoints)
        assertEquals(0, state.pointCount)
        assertNull(state.latestPoint)
        assertEquals(LiveMapStatus.WAITING_FIRST, state.status)
    }

    @Test
    fun sessionWithPointsUsesLatestPointAndPathCount() {
        val points = listOf(
            LocationPoint(1, "+391", "s1", 37.1, 15.1, 8f, 1_000L),
            LocationPoint(2, "+391", "s1", 37.2, 15.2, 6f, 11_000L)
        )

        val state = LiveMapStateFactory.fromPoints(points, isLive = true, nowMs = 11_000L)

        assertTrue(state.hasPoints)
        assertEquals(2, state.pointCount)
        assertEquals(37.2, state.latestPoint?.lat ?: 0.0, 0.0)
        assertEquals(15.2, state.latestPoint?.lon ?: 0.0, 0.0)
        assertEquals(LiveMapStatus.LIVE, state.status)
        assertEquals(0L, state.ageSeconds)
    }

    @Test
    fun staticSessionUsesHistoricalStatus() {
        val points = listOf(
            LocationPoint(1, "+391", "s1", 37.1, 15.1, 8f, 1_000L)
        )

        val state = LiveMapStateFactory.fromPoints(points, isLive = false, nowMs = 0L)

        assertEquals(LiveMapStatus.HISTORY, state.status)
        assertEquals(1, state.pointCount)
    }

    @Test
    fun live_showsLastUpdateAge() {
        val p = LocationPoint(1, "+39", "s", 1.0, 2.0, 5f, 100_000L)
        val state = LiveMapStateFactory.fromPoints(listOf(p), isLive = true, nowMs = 108_000L)
        assertEquals(LiveMapStatus.LIVE, state.status)
        assertEquals(8L, state.ageSeconds)
    }

    @Test
    fun live_recentUpdate_showsNow() {
        val p = LocationPoint(1, "+39", "s", 1.0, 2.0, 5f, 100_000L)
        val state = LiveMapStateFactory.fromPoints(listOf(p), isLive = true, nowMs = 105_000L)
        assertEquals(LiveMapStatus.LIVE, state.status)
        assertEquals(5L, state.ageSeconds)
    }

    @Test
    fun sessionEnded_showsTerminated() {
        val state = LiveMapStateFactory.fromPoints(emptyList(), isLive = false, nowMs = 0L, sessionEnded = true)
        assertEquals(LiveMapStatus.ENDED, state.status)
    }
}
