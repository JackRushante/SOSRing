package com.lorenzomarci.sosring

/**
 * Live tracking listens to GPS and network location together, so a phone indoors (no GPS fix)
 * still shares its Wi-Fi/cell position. This picks which fixes go out: about one per interval,
 * and no network fix while GPS is delivering.
 */
object LiveFixFilter {
    /** A fix may go out a little before the full interval: providers do not tick exactly. */
    private const val INTERVAL_SLACK = 0.8

    /** GPS counts as delivering while its last fix is younger than this many intervals. */
    private const val GPS_FRESH_INTERVALS = 3

    fun shouldDeliver(
        isGps: Boolean,
        nowMs: Long,
        lastDeliveredAtMs: Long,
        lastGpsAtMs: Long,
        intervalMs: Long
    ): Boolean {
        if (!isGps && lastGpsAtMs > 0L && nowMs - lastGpsAtMs < intervalMs * GPS_FRESH_INTERVALS) return false
        if (lastDeliveredAtMs <= 0L) return true
        return nowMs - lastDeliveredAtMs >= (intervalMs * INTERVAL_SLACK).toLong()
    }
}
