package com.lorenzomarci.sosring

/**
 * Retry per i messaggi di controllo (live_start/live_stop): a differenza dei
 * punti live (lossy per natura), la loro perdita lascia sessioni appese.
 */
object ControlRetryPolicy {
    val DELAYS_MS = longArrayOf(0L, 3_000L, 10_000L)
    val MAX_ATTEMPTS = DELAYS_MS.size

    fun delayBeforeAttempt(attempt: Int): Long =
        DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.size - 1)]
}
