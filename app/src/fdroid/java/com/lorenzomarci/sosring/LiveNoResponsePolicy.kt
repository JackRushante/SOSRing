package com.lorenzomarci.sosring

object LiveNoResponsePolicy {
    const val NO_RESPONSE_MS = 40_000L

    fun timedOut(startedAtMs: Long, firstPointReceived: Boolean, nowMs: Long): Boolean =
        !firstPointReceived && nowMs - startedAtMs >= NO_RESPONSE_MS
}
