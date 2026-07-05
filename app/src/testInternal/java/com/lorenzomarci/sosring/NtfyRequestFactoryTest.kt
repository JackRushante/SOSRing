package com.lorenzomarci.sosring

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyRequestFactoryTest {

    @Test
    fun sseRequestUsesBearerTokenWhenConfigured() {
        val request = NtfyRequestFactory.sse(
            serverUrl = "https://push.example.com/",
            topic = "sosring-topic",
            token = "token-123"
        )

        assertEquals("https://push.example.com/sosring-topic/sse", request.url.toString())
        assertEquals("Bearer token-123", request.header("Authorization"))
    }

    @Test
    fun publishRequestUsesSameBearerTokenAsSse() {
        val request = NtfyRequestFactory.publish(
            serverUrl = "https://push.example.com",
            topic = "sosring-topic",
            message = JSONObject().put("type", "loc_request"),
            token = "token-123"
        )

        assertEquals("https://push.example.com/sosring-topic", request.url.toString())
        assertEquals("Bearer token-123", request.header("Authorization"))
    }

    @Test
    fun blankTokenDoesNotSendAuthorizationHeader() {
        val request = NtfyRequestFactory.sse(
            serverUrl = "https://push.example.com",
            topic = "sosring-topic",
            token = " "
        )

        assertNull(request.header("Authorization"))
    }

    @Test
    fun sseWithoutSinceHasNoQueryString() {
        val request = NtfyRequestFactory.sse(
            serverUrl = "https://push.example.com",
            topic = "sosring-topic",
            token = "t"
        )

        assertNull(request.url.query)
    }

    @Test
    fun sseReconnectRequestsCatchUpSinceLastEvent() {
        val request = NtfyRequestFactory.sse(
            serverUrl = "https://push.example.com",
            topic = "sosring-topic",
            token = "t",
            sinceUnixSeconds = 1_770_000_000L
        )

        assertEquals(
            "https://push.example.com/sosring-topic/sse?since=1770000000",
            request.url.toString()
        )
    }

    @Test
    fun sseIgnoresNonPositiveSince() {
        val request = NtfyRequestFactory.sse(
            serverUrl = "https://push.example.com",
            topic = "sosring-topic",
            token = "t",
            sinceUnixSeconds = 0L
        )

        assertNull(request.url.query)
    }
}
