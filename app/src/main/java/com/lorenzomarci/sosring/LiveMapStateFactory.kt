package com.lorenzomarci.sosring

enum class LiveMapStatus { WAITING_FIRST, NO_POINTS, LIVE, HISTORY, ENDED }

data class LiveMapState(
    val latestPoint: LocationPoint?,
    val pointCount: Int,
    val status: LiveMapStatus,
    val ageSeconds: Long = 0L
) {
    val hasPoints: Boolean = latestPoint != null
}

object LiveMapStateFactory {
    fun fromPoints(
        points: List<LocationPoint>,
        isLive: Boolean,
        nowMs: Long,
        sessionEnded: Boolean = false
    ): LiveMapState {
        val latest = points.lastOrNull()
        val status = when {
            sessionEnded -> LiveMapStatus.ENDED
            latest == null && isLive -> LiveMapStatus.WAITING_FIRST
            latest == null -> LiveMapStatus.NO_POINTS
            isLive -> LiveMapStatus.LIVE
            else -> LiveMapStatus.HISTORY
        }
        val ageSeconds = if (latest != null) ((nowMs - latest.timestamp) / 1000).coerceAtLeast(0) else 0L
        return LiveMapState(
            latestPoint = latest,
            pointCount = points.size,
            status = status,
            ageSeconds = ageSeconds
        )
    }
}
