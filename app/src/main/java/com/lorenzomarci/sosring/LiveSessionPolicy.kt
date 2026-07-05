package com.lorenzomarci.sosring

/**
 * Scadenza e staleness delle sessioni live su orologio a muro.
 * I timer Handler/postDelayed usano uptimeMillis, che si ferma in deep sleep:
 * la fine sessione va sempre verificata anche contro System.currentTimeMillis().
 */
object LiveSessionPolicy {
    const val STALE_FACTOR = 3
    const val MIN_STALE_MS = 30_000L
    const val WATCHDOG_TICK_MS = 15_000L

    fun deadlineMs(startedAtMs: Long, durationMinutes: Int): Long =
        startedAtMs + durationMinutes * 60_000L

    fun isExpired(deadlineMs: Long, nowMs: Long): Boolean = nowMs >= deadlineMs

    fun staleAfterMs(intervalMillis: Long): Long =
        maxOf(intervalMillis * STALE_FACTOR, MIN_STALE_MS)

    fun isStale(lastUpdateAtMs: Long, intervalMillis: Long, nowMs: Long): Boolean =
        lastUpdateAtMs > 0L && nowMs - lastUpdateAtMs >= staleAfterMs(intervalMillis)
}
