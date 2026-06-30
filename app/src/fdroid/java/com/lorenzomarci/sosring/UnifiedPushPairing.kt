package com.lorenzomarci.sosring

import org.json.JSONObject

data class PairPayload(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val idPub: String? = null
)

object UnifiedPushPairing {

    private const val PREFIX = "sosup1:"

    fun encode(payload: PairPayload): String {
        val json = JSONObject().apply {
            put("e", payload.endpoint)
            put("p", payload.p256dh)
            put("a", payload.auth)
            payload.idPub?.let { put("i", it) }
        }
        return PREFIX + WebPushCrypto.b64enc(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(text: String): PairPayload? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        return try {
            val decoded = String(WebPushCrypto.b64dec(trimmed.removePrefix(PREFIX)), Charsets.UTF_8)
            val json = JSONObject(decoded)
            val endpoint = json.optString("e", "")
            val p256dh = json.optString("p", "")
            val auth = json.optString("a", "")
            if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) return null
            val idPub = if (json.has("i")) json.optString("i", "").ifBlank { null } else null
            PairPayload(endpoint, p256dh, auth, idPub)
        } catch (e: Exception) {
            null
        }
    }
}
