package com.lorenzomarci.sosring

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NtfyRequestFactory {
    private val JSON_TYPE = "application/json".toMediaType()

    fun publish(serverUrl: String, topic: String, message: JSONObject, token: String): Request {
        val body = message.toString().toRequestBody(JSON_TYPE)
        return Request.Builder()
            .url("${serverUrl.trimEnd('/')}/$topic")
            .post(body)
            .withAuth(token)
            .build()
    }

    fun sse(serverUrl: String, topic: String, token: String): Request {
        return Request.Builder()
            .url("${serverUrl.trimEnd('/')}/$topic/sse")
            .withAuth(token)
            .build()
    }

    private fun Request.Builder.withAuth(token: String): Request.Builder {
        val trimmed = token.trim()
        if (trimmed.isNotBlank()) {
            addHeader("Authorization", "Bearer $trimmed")
        }
        return this
    }
}
