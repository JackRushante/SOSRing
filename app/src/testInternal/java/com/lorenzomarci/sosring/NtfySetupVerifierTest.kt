package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class NtfySetupVerifierTest {

    @Test
    fun blankServerIsInvalidUrl() {
        assertEquals(
            NtfySetupStatus.INVALID_URL,
            NtfySetupVerifier.classify(serverUrl = "", topic = "sosring-topic", token = "", healthCode = null, publishCode = null)
        )
    }

    @Test
    fun missingTopicRequiresPhoneNumber() {
        assertEquals(
            NtfySetupStatus.MISSING_OWN_NUMBER,
            NtfySetupVerifier.classify(serverUrl = "https://ntfy.example.com", topic = "", token = "", healthCode = 200, publishCode = null)
        )
    }

    @Test
    fun serverWithAnonymousPublishIsReportedAsNoAuth() {
        assertEquals(
            NtfySetupStatus.CONNECTED_NO_AUTH,
            NtfySetupVerifier.classify(serverUrl = "https://ntfy.example.com", topic = "sosring-topic", token = "", healthCode = 200, publishCode = 200)
        )
    }

    @Test
    fun serverWithTokenPublishIsReportedAsAuthenticated() {
        assertEquals(
            NtfySetupStatus.CONNECTED_AUTH,
            NtfySetupVerifier.classify(
                serverUrl = "https://ntfy.example.com",
                topic = "sosring-topic",
                token = "secret",
                healthCode = 200,
                publishCode = 200,
                anonymousPublishCode = 401
            )
        )
    }

    @Test
    fun tokenAcceptedOnAnonymousServerIsReportedAsIgnored() {
        assertEquals(
            NtfySetupStatus.CONNECTED_NO_AUTH_TOKEN_IGNORED,
            NtfySetupVerifier.classify(
                serverUrl = "https://ntfy.example.com",
                topic = "sosring-topic",
                token = "random",
                healthCode = 200,
                publishCode = 200,
                anonymousPublishCode = 200
            )
        )
    }

    @Test
    fun unauthorizedWithoutTokenMeansTokenRequired() {
        assertEquals(
            NtfySetupStatus.TOKEN_REQUIRED,
            NtfySetupVerifier.classify(serverUrl = "https://ntfy.example.com", topic = "sosring-topic", token = "", healthCode = 200, publishCode = 401)
        )
    }

    @Test
    fun unauthorizedWithTokenMeansTokenRejected() {
        assertEquals(
            NtfySetupStatus.TOKEN_REJECTED,
            NtfySetupVerifier.classify(serverUrl = "https://ntfy.example.com", topic = "sosring-topic", token = "wrong", healthCode = 200, publishCode = 403)
        )
    }

    @Test
    fun failedHealthMeansServerUnreachable() {
        assertEquals(
            NtfySetupStatus.SERVER_UNREACHABLE,
            NtfySetupVerifier.classify(serverUrl = "https://ntfy.example.com", topic = "sosring-topic", token = "", healthCode = 503, publishCode = null)
        )
    }

    @Test
    fun publish429_isRateLimited() {
        val s = NtfySetupVerifier.classify("https://x", "topic", "tk", healthCode = 200, publishCode = 429)
        assertEquals(NtfySetupStatus.RATE_LIMITED, s)
    }

    @Test
    fun health429_isRateLimited() {
        val s = NtfySetupVerifier.classify("https://x", "topic", "tk", healthCode = 429, publishCode = null)
        assertEquals(NtfySetupStatus.RATE_LIMITED, s)
    }
}
