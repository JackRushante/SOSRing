package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pDeliveryTest {

    @Test
    fun pushFailureNamesTheCause() {
        assertEquals(PushFailure.STALE_PAIRING, PushFailure.fromStatus(404))
        assertEquals(PushFailure.STALE_PAIRING, PushFailure.fromStatus(410))
        assertEquals(PushFailure.TOO_LARGE, PushFailure.fromStatus(413))
        assertEquals(PushFailure.RATE_LIMITED, PushFailure.fromStatus(429))
        assertEquals(PushFailure.SERVER_ERROR, PushFailure.fromStatus(500))
        assertEquals(PushFailure.SERVER_ERROR, PushFailure.fromStatus(401))
        assertEquals(PushFailure.NETWORK, PushFailure.fromStatus(null))
    }

    @Test
    fun onlyGoneOrOversizedIsNotRetried() {
        assertTrue(PushFailure.isPermanent(404))
        assertTrue(PushFailure.isPermanent(410))
        assertTrue(PushFailure.isPermanent(413))
        assertFalse(PushFailure.isPermanent(429))
        assertFalse(PushFailure.isPermanent(503))
    }

    @Test
    fun controlMessagesLiveAsLongAsTheReplayWindow() {
        assertEquals(300, WebPushSender.CONTROL_TTL_SECONDS)
        val request = WebPushSender.buildRequest("https://push.example.com/up1", byteArrayOf(0), 300)
        assertEquals("300", request.header("TTL"))
        assertEquals("high", request.header("Urgency"))
    }

    @Test
    fun resendIsHeldBackInsideTheReplayGuardInterval() {
        assertEquals(0L, P2pRequestPolicy.waitBeforeResendMs(null, 1_000L))
        assertEquals(20_000L, P2pRequestPolicy.waitBeforeResendMs(0L, 10_000L))
        assertEquals(0L, P2pRequestPolicy.waitBeforeResendMs(0L, 30_000L))
        assertEquals(0L, P2pRequestPolicy.waitBeforeResendMs(50_000L, 10_000L))
    }

    @Test
    fun requesterWaitsLongerThanTheFixTimeoutPlusRetries() {
        val retries = ControlRetryPolicy.DELAYS_MS.sum()
        assertTrue(P2pRequestPolicy.ANSWER_TIMEOUT_MS > 30_000L + 2 * retries)
    }

    @Test
    fun liveFilterSendsOnePerIntervalAndPrefersGps() {
        val interval = 10_000L
        assertTrue(LiveFixFilter.shouldDeliver(false, 1_000L, 0L, 0L, interval))
        assertFalse(LiveFixFilter.shouldDeliver(false, 5_000L, 1_000L, 0L, interval))
        assertTrue(LiveFixFilter.shouldDeliver(false, 9_500L, 1_000L, 0L, interval))
        // a GPS fix 5 s ago: the network fix is dropped
        assertFalse(LiveFixFilter.shouldDeliver(false, 40_000L, 20_000L, 35_000L, interval))
        // GPS silent for 30 s (indoors): network takes over again
        assertTrue(LiveFixFilter.shouldDeliver(false, 70_000L, 40_000L, 40_000L, interval))
        assertTrue(LiveFixFilter.shouldDeliver(true, 70_000L, 60_000L, 70_000L, interval))
    }

    @Test
    fun locErrorCarriesTheLiveSession() {
        val live = P2pMessageFactory.locError("no_fix", "sid-1")
        assertEquals("no_fix", P2pMessageFactory.parseLocError(live))
        assertEquals("sid-1", P2pMessageFactory.parseLocErrorSession(live))
        assertNull(P2pMessageFactory.parseLocErrorSession(P2pMessageFactory.locError("no_fix")))
        assertNull(P2pMessageFactory.parseLocErrorSession(P2pMessageFactory.locRequest()))
    }

    @Test
    fun endpointUpdateRoundTripAndValidation() {
        val bytes = P2pMessageFactory.endpointUpdate("https://push.example.com/up2", "pk", "au")
        assertEquals("ep_upd", P2pMessageFactory.type(bytes))
        assertTrue(P2pMessageFactory.timestamp(bytes)!! > 0L)
        val parsed = P2pMessageFactory.parseEndpointUpdate(bytes)!!
        assertEquals("https://push.example.com/up2", parsed.endpoint)
        assertEquals("pk", parsed.p256dh)
        assertEquals("au", parsed.auth)
        assertNull(P2pMessageFactory.parseEndpointUpdate(P2pMessageFactory.endpointUpdate("ftp://x", "pk", "au")))
        assertNull(P2pMessageFactory.parseEndpointUpdate(P2pMessageFactory.endpointUpdate("https://x", "", "au")))
        assertNull(P2pMessageFactory.parseEndpointUpdate(P2pMessageFactory.locRequest()))
    }
}
