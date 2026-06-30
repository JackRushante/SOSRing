package com.lorenzomarci.sosring

object LiveLocationBudget {
    fun maxUpdates(durationMillis: Long, intervalMillis: Long): Int {
        if (intervalMillis <= 0L) return 1
        val count = (durationMillis + intervalMillis - 1) / intervalMillis
        return count.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
