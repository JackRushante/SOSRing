package com.lorenzomarci.sosring

import android.content.Context

class LiveConsentUsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("sosring_live_budget", Context.MODE_PRIVATE)

    fun usedMsToday(peerNumber: String, nowMs: Long = System.currentTimeMillis()): Long =
        prefs.getLong(key(peerNumber, epochDay(nowMs)), 0L)

    fun addUsage(peerNumber: String, elapsedMs: Long, nowMs: Long = System.currentTimeMillis()) {
        if (elapsedMs <= 0L) return
        val k = key(peerNumber, epochDay(nowMs))
        val used = prefs.getLong(k, 0L)
        prefs.edit().putLong(k, used + elapsedMs).apply()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        internal fun epochDay(nowMs: Long): Long = nowMs / DAY_MS

        internal fun key(peerNumber: String, day: Long) = "used_${peerNumber}_$day"
    }
}
