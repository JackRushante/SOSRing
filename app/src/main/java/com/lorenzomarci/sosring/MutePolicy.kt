package com.lorenzomarci.sosring

object MutePolicy {
    fun isMuted(untilMs: Long, nowMs: Long): Boolean {
        if (untilMs == 0L) return false
        return nowMs < untilMs
    }
}
