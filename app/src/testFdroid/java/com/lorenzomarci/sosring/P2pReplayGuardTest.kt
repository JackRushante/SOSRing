package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class P2pReplayGuardTest {

    private val now = 1_000_000_000_000L

    @Test
    fun acceptsFreshMonotonicTimestamp() {
        assertEquals(
            FreshnessVerdict.ACCEPT,
            P2pFreshness.evaluate(
                ts = now,
                now = now,
                lastSeenTs = 0L,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsMissingTimestamp() {
        assertEquals(
            FreshnessVerdict.MISSING_TS,
            P2pFreshness.evaluate(
                ts = null,
                now = now,
                lastSeenTs = 0L,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsNonPositiveTimestamp() {
        assertEquals(
            FreshnessVerdict.MISSING_TS,
            P2pFreshness.evaluate(
                ts = 0L,
                now = now,
                lastSeenTs = 0L,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsTimestampOlderThanSkewWindow() {
        assertEquals(
            FreshnessVerdict.TOO_OLD,
            P2pFreshness.evaluate(
                ts = now - P2pFreshness.SKEW_WINDOW_MS - 1L,
                now = now,
                lastSeenTs = 0L,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsTimestampTooFarInFuture() {
        assertEquals(
            FreshnessVerdict.TOO_FUTURE,
            P2pFreshness.evaluate(
                ts = now + P2pFreshness.SKEW_WINDOW_MS + 1L,
                now = now,
                lastSeenTs = 0L,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsReplayOfLastSeenTimestamp() {
        assertEquals(
            FreshnessVerdict.NOT_MONOTONIC,
            P2pFreshness.evaluate(
                ts = now,
                now = now,
                lastSeenTs = now,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rejectsTimestampOlderThanLastSeen() {
        assertEquals(
            FreshnessVerdict.NOT_MONOTONIC,
            P2pFreshness.evaluate(
                ts = now - 1L,
                now = now,
                lastSeenTs = now,
                lastHonoredTs = 0L,
                enforceRateLimit = false
            )
        )
    }

    @Test
    fun rateLimitsRequestsWithinMinInterval() {
        assertEquals(
            FreshnessVerdict.RATE_LIMITED,
            P2pFreshness.evaluate(
                ts = now + 1L,
                now = now,
                lastSeenTs = now - 100L,
                lastHonoredTs = now - (P2pFreshness.MIN_REQUEST_INTERVAL_MS - 1L),
                enforceRateLimit = true
            )
        )
    }

    @Test
    fun allowsRequestAfterMinInterval() {
        assertEquals(
            FreshnessVerdict.ACCEPT,
            P2pFreshness.evaluate(
                ts = now + 1L,
                now = now,
                lastSeenTs = now - 100L,
                lastHonoredTs = now - P2pFreshness.MIN_REQUEST_INTERVAL_MS,
                enforceRateLimit = true
            )
        )
    }

    @Test
    fun rateLimitNotAppliedToResponses() {
        assertEquals(
            FreshnessVerdict.ACCEPT,
            P2pFreshness.evaluate(
                ts = now + 1L,
                now = now,
                lastSeenTs = now - 100L,
                lastHonoredTs = now - 1L,
                enforceRateLimit = false
            )
        )
    }
}
