package com.lorenzomarci.sosring

object LiveConsentBudget {
    const val DAILY_CAP_MS = 4L * 60L * 60L * 1000L

    fun allow(usedMsToday: Long, requestedMs: Long, dailyCapMs: Long): Boolean =
        usedMsToday + requestedMs <= dailyCapMs
}
