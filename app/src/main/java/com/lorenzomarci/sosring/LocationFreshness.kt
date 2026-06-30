package com.lorenzomarci.sosring

object LocationFreshness {
    const val MAX_AGE_MS = 5 * 60 * 1000L

    fun isFresh(fixTimeMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        return fixTimeMs > 0L && nowMs - fixTimeMs <= maxAgeMs
    }
}
