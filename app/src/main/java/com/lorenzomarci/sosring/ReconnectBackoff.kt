package com.lorenzomarci.sosring

object ReconnectBackoff {
    const val BASE_MS = 2000L
    const val MAX_MS = 60000L

    fun delayMs(attempt: Int): Long {
        if (attempt <= 0) return BASE_MS
        val shifted = BASE_MS shl attempt.coerceAtMost(20)
        return shifted.coerceAtMost(MAX_MS)
    }
}
