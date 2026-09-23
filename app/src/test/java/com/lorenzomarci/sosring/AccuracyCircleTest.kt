package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyCircleTest {

    @Test
    fun ringIsClosedWithExpectedVertexCount() {
        val ring = AccuracyCircle.ring(45.0, 9.0, 30.0, steps = 48)

        assertEquals(49, ring.size)
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun everyVertexLiesAtRadiusFromCenter() {
        listOf(0.0, 45.0, 64.0).forEach { lat ->
            val ring = AccuracyCircle.ring(lat, 9.0, 50.0)
            ring.forEach { (lon, vertexLat) ->
                val d = LiveTrackStatsPolicy.distanceMeters(lat, 9.0, vertexLat, lon)
                assertEquals(50.0, d, 0.5)
            }
        }
    }

    @Test
    fun nonPositiveRadiusHasNoRing() {
        assertTrue(AccuracyCircle.ring(45.0, 9.0, 0.0).isEmpty())
        assertTrue(AccuracyCircle.ring(45.0, 9.0, -3.0).isEmpty())
    }
}
