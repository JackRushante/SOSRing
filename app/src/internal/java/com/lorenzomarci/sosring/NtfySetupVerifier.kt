package com.lorenzomarci.sosring

import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class NtfySetupStatus {
    CONNECTED_AUTH,
    CONNECTED_NO_AUTH,
    CONNECTED_NO_AUTH_TOKEN_IGNORED,
    TOKEN_REQUIRED,
    TOKEN_REJECTED,
    RATE_LIMITED,
    SERVER_UNREACHABLE,
    INVALID_URL,
    MISSING_OWN_NUMBER
}

object NtfySetupVerifier {
    fun classify(
        serverUrl: String,
        topic: String,
        token: String,
        healthCode: Int?,
        publishCode: Int?,
        anonymousPublishCode: Int? = null
    ): NtfySetupStatus {
        if (!isValidServerUrl(serverUrl)) return NtfySetupStatus.INVALID_URL
        if (topic.isBlank()) return NtfySetupStatus.MISSING_OWN_NUMBER
        if (healthCode == 429) return NtfySetupStatus.RATE_LIMITED
        if (healthCode == null || healthCode !in 200..299) return NtfySetupStatus.SERVER_UNREACHABLE

        return when {
            publishCode == 429 -> NtfySetupStatus.RATE_LIMITED
            publishCode in 200..299 && token.isNotBlank() && anonymousPublishCode in 200..299 ->
                NtfySetupStatus.CONNECTED_NO_AUTH_TOKEN_IGNORED
            publishCode in 200..299 && token.isBlank() -> NtfySetupStatus.CONNECTED_NO_AUTH
            publishCode in 200..299 -> NtfySetupStatus.CONNECTED_AUTH
            publishCode == 401 || publishCode == 403 -> {
                if (token.isBlank()) NtfySetupStatus.TOKEN_REQUIRED else NtfySetupStatus.TOKEN_REJECTED
            }
            else -> NtfySetupStatus.SERVER_UNREACHABLE
        }
    }

    fun verify(serverUrl: String, topic: String, token: String): NtfySetupStatus {
        if (!isValidServerUrl(serverUrl)) return NtfySetupStatus.INVALID_URL
        if (topic.isBlank()) return NtfySetupStatus.MISSING_OWN_NUMBER

        return try {
            val baseUrl = serverUrl.trimEnd('/')
            val client = NetworkClient.client.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()

            val healthCode = client.newCall(
                Request.Builder().url("$baseUrl/v1/health").build()
            ).execute().use { it.code }

            var anonymousPublishCode: Int? = null
            val publishCode = if (healthCode in 200..299) {
                val message = JSONObject().apply {
                    put("type", "setup_probe")
                    put("from", topic)
                    put("ts", System.currentTimeMillis() / 1000)
                }
                if (token.isNotBlank()) {
                    anonymousPublishCode = client.newCall(
                        NtfyRequestFactory.publish(baseUrl, topic, message, "")
                    ).execute().use { it.code }
                }
                client.newCall(
                    NtfyRequestFactory.publish(baseUrl, topic, message, token)
                ).execute().use { it.code }
            } else {
                null
            }

            classify(serverUrl, topic, token, healthCode, publishCode, anonymousPublishCode)
        } catch (_: Exception) {
            NtfySetupStatus.SERVER_UNREACHABLE
        }
    }

    private fun isValidServerUrl(serverUrl: String): Boolean {
        val trimmed = serverUrl.trim()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }
}
