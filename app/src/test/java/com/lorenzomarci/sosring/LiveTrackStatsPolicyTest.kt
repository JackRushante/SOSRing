package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackStatsPolicyTest {

    private val baseLat = 45.0
    private val baseLon = 9.0
    private val metersPerDegreeLat = 111_195.0

    private fun point(id: Long, northMeters: Double, timestampMs: Long, accuracy: Float = 5f): LocationPoint {
        return LocationPoint(id, "+391", "s1", baseLat + northMeters / metersPerDegreeLat, baseLon, accuracy, timestampMs)
    }

    @Test
    fun fewerThanTwoPointsHasNoStats() {
        assertNull(LiveTrackStatsPolicy.compute(emptyList(), includeRecent = true))
        assertNull(LiveTrackStatsPolicy.compute(listOf(point(1, 0.0, 0L)), includeRecent = true))
    }

    @Test
    fun sessionShorterThanMinimumDurationHasNoStats() {
        val points = listOf(point(1, 0.0, 0L), point(2, 30.0, 20_000L))

        assertNull(LiveTrackStatsPolicy.compute(points, includeRecent = true))
    }

    @Test
    fun steadyWalkReportsDistanceDurationAndSpeed() {
        val points = (0..12).map { point(it.toLong(), it * 14.0, it * 10_000L) }

        val stats = LiveTrackStatsPolicy.compute(points, includeRecent = true)

        assertNotNull(stats)
        stats!!
        assertEquals(120_000L, stats.durationMs)
        assertEquals(168.0, stats.distanceMeters, 1.0)
        assertEquals(5.04, stats.averageKmh, 0.05)
        assertEquals(5.04, stats.recentKmh!!, 0.05)
    }

    @Test
    fun stationaryJitterWithinAccuracyIsIgnored() {
        val offsets = listOf(0.0, 6.0, -4.0, 8.0, -7.0, 3.0, -5.0, 7.0)
        val points = offsets.mapIndexed { i, offset -> point(i.toLong(), offset, i * 10_000L, accuracy = 10f) }

        val stats = LiveTrackStatsPolicy.compute(points, includeRecent = true)!!

        assertEquals(0.0, stats.distanceMeters, 0.001)
        assertEquals(0.0, stats.averageKmh, 0.001)
    }

    @Test
    fun slowMovementBelowAccuracyPerStepIsStillCounted() {
        val points = (0..10).map { point(it.toLong(), it * 4.0, it * 10_000L, accuracy = 10f) }

        val stats = LiveTrackStatsPolicy.compute(points, includeRecent = false)!!

        assertTrue(stats.distanceMeters >= 36.0)
        assertTrue(stats.distanceMeters <= 40.5)
    }

    @Test
    fun inaccuratePointsAreDiscarded() {
        val points = listOf(
            point(1, 0.0, 0L),
            point(2, 5_000.0, 20_000L, accuracy = 250f),
            point(3, 40.0, 40_000L)
        )

        val stats = LiveTrackStatsPolicy.compute(points, includeRecent = false)!!

        assertEquals(40.0, stats.distanceMeters, 0.5)
        assertEquals(40_000L, stats.durationMs)
    }

    @Test
    fun recentSpeedUsesOnlyLastWindow() {
        val slow = (0..12).map { point(it.toLong(), it * 10.0, it * 10_000L) }
        val fast = (1..6).map { point(100L + it, 120.0 + it * 50.0, 120_000L + it * 10_000L) }

        val stats = LiveTrackStatsPolicy.compute(slow + fast, includeRecent = true)!!

        assertEquals(18.0, stats.recentKmh!!, 0.2)
        assertTrue(stats.averageKmh < stats.recentKmh!!)
    }

    @Test
    fun recentSpeedHiddenWhenWindowSpanTooShort() {
        val early = (0..6).map { point(it.toLong(), it * 14.0, it * 10_000L) }
        val burst = (1..5).map { point(100L + it, 84.0 + it * 14.0, 300_000L + it * 200L) }

        val stats = LiveTrackStatsPolicy.compute(early + burst, includeRecent = true)!!

        assertNull(stats.recentKmh)
        assertTrue(stats.averageKmh > 0.0)
    }

    @Test
    fun recentSpeedOmittedWhenNotRequested() {
        val points = (0..12).map { point(it.toLong(), it * 14.0, it * 10_000L) }

        assertNull(LiveTrackStatsPolicy.compute(points, includeRecent = false)!!.recentKmh)
    }

    @Test
    fun distanceMetersMatchesKnownLatitudeDegree() {
        assertEquals(111_195.0, LiveTrackStatsPolicy.distanceMeters(45.0, 9.0, 46.0, 9.0), 5.0)
    }
}
