package com.lorenzomarci.sosring

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LiveTrackStats(
    val durationMs: Long,
    val distanceMeters: Double,
    val averageKmh: Double,
    val recentKmh: Double?
)

object LiveTrackStatsPolicy {
    const val MAX_ACCURACY_METERS = 100f
    const val MIN_STEP_METERS = 5.0
    const val MIN_DURATION_MS = 30_000L
    const val RECENT_WINDOW_MS = 60_000L
    const val MIN_RECENT_SPAN_MS = 20_000L
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun compute(points: List<LocationPoint>, includeRecent: Boolean): LiveTrackStats? {
        val usable = points.filter { it.accuracy <= MAX_ACCURACY_METERS }.sortedBy { it.timestamp }
        if (usable.size < 2) return null
        val durationMs = usable.last().timestamp - usable.first().timestamp
        if (durationMs < MIN_DURATION_MS) return null
        val distance = filteredDistance(usable)
        return LiveTrackStats(
            durationMs = durationMs,
            distanceMeters = distance,
            averageKmh = kmh(distance, durationMs),
            recentKmh = if (includeRecent) recentKmh(usable) else null
        )
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    private fun recentKmh(points: List<LocationPoint>): Double? {
        val end = points.last().timestamp
        val window = points.filter { it.timestamp >= end - RECENT_WINDOW_MS }
        if (window.size < 2) return null
        val span = end - window.first().timestamp
        if (span < MIN_RECENT_SPAN_MS) return null
        return kmh(filteredDistance(window), span)
    }

    private fun filteredDistance(points: List<LocationPoint>): Double {
        var anchor = points.first()
        var total = 0.0
        for (point in points.drop(1)) {
            val step = distanceMeters(anchor.lat, anchor.lon, point.lat, point.lon)
            val threshold = max(MIN_STEP_METERS, max(anchor.accuracy, point.accuracy).toDouble())
            if (step > threshold) {
                total += step
                anchor = point
            }
        }
        return total
    }

    private fun kmh(distanceMeters: Double, durationMs: Long): Double {
        if (durationMs <= 0L) return 0.0
        return distanceMeters / durationMs * 3_600.0
    }
}
