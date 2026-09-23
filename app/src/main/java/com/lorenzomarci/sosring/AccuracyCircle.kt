package com.lorenzomarci.sosring

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object AccuracyCircle {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun ring(lat: Double, lon: Double, radiusMeters: Double, steps: Int = 48): List<Pair<Double, Double>> {
        if (radiusMeters <= 0.0 || steps < 3) return emptyList()
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)
        val delta = radiusMeters / EARTH_RADIUS_METERS
        val vertices = (0 until steps).map { i ->
            val theta = 2 * Math.PI * i / steps
            val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
            val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
            Math.toDegrees(lambda2) to Math.toDegrees(phi2)
        }
        return vertices + vertices.first()
    }
}
