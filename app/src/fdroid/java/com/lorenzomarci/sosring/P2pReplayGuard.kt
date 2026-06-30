package com.lorenzomarci.sosring

import android.content.Context

enum class FreshnessVerdict {
    ACCEPT,
    MISSING_TS,
    TOO_OLD,
    TOO_FUTURE,
    NOT_MONOTONIC,
    RATE_LIMITED
}

object P2pFreshness {

    const val SKEW_WINDOW_MS = 5L * 60L * 1000L
    const val MIN_REQUEST_INTERVAL_MS = 30L * 1000L

    fun evaluate(
        ts: Long?,
        now: Long,
        lastSeenTs: Long,
        lastHonoredTs: Long,
        enforceRateLimit: Boolean
    ): FreshnessVerdict {
        if (ts == null || ts <= 0L) return FreshnessVerdict.MISSING_TS
        if (ts < now - SKEW_WINDOW_MS) return FreshnessVerdict.TOO_OLD
        if (ts > now + SKEW_WINDOW_MS) return FreshnessVerdict.TOO_FUTURE
        if (ts <= lastSeenTs) return FreshnessVerdict.NOT_MONOTONIC
        if (enforceRateLimit && lastHonoredTs > 0L && now - lastHonoredTs < MIN_REQUEST_INTERVAL_MS) {
            return FreshnessVerdict.RATE_LIMITED
        }
        return FreshnessVerdict.ACCEPT
    }
}

class P2pReplayGuard(context: Context) {

    private val prefs = context.getSharedPreferences("sosring_replay", Context.MODE_PRIVATE)

    fun check(senderIdPubB64: String, ts: Long?, now: Long, enforceRateLimit: Boolean): FreshnessVerdict {
        val verdict = P2pFreshness.evaluate(
            ts = ts,
            now = now,
            lastSeenTs = prefs.getLong(seenKey(senderIdPubB64), 0L),
            lastHonoredTs = prefs.getLong(honoredKey(senderIdPubB64), 0L),
            enforceRateLimit = enforceRateLimit
        )
        if (verdict == FreshnessVerdict.ACCEPT && ts != null) {
            val editor = prefs.edit().putLong(seenKey(senderIdPubB64), ts)
            if (enforceRateLimit) editor.putLong(honoredKey(senderIdPubB64), now)
            editor.apply()
        }
        return verdict
    }

    private fun seenKey(senderIdPubB64: String) = "seen_$senderIdPubB64"

    private fun honoredKey(senderIdPubB64: String) = "honored_$senderIdPubB64"
}
