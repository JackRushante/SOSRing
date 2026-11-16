package com.lorenzomarci.sosring

object LiveNoResponsePolicy {
    /** Requester side: no position and no error from the other phone within this time. */
    const val NO_RESPONSE_MS = 60_000L

    /**
     * Sharing side: no fix this long after the start is reported to the requester, early enough
     * to arrive before [NO_RESPONSE_MS] (the watchdog ticks every 15 s).
     */
    const val FIRST_FIX_LATE_MS = 30_000L

    fun timedOut(startedAtMs: Long, firstPointReceived: Boolean, nowMs: Long): Boolean =
        !firstPointReceived && nowMs - startedAtMs >= NO_RESPONSE_MS

    fun firstFixLate(startedAtMs: Long, nowMs: Long): Boolean =
        startedAtMs > 0L && nowMs - startedAtMs >= FIRST_FIX_LATE_MS
}
