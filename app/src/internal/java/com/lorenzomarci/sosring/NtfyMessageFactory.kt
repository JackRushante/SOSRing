package com.lorenzomarci.sosring

import org.json.JSONObject

object NtfyMessageFactory {

    fun discovery(fromHash: String, keyFingerprint: String?): JSONObject =
        base("discovery", fromHash).apply {
            keyFingerprint?.let { put("key_fp", it) }
        }

    fun discoveryAck(fromHash: String, keyFingerprint: String?): JSONObject =
        base("discovery_ack", fromHash).apply {
            keyFingerprint?.let { put("key_fp", it) }
        }

    fun keyRotated(fromHash: String, keyFingerprint: String?): JSONObject =
        base("key_rotated", fromHash).apply {
            keyFingerprint?.let { put("key_fp", it) }
        }

    fun locationRequest(fromHash: String): JSONObject =
        base("loc_request", fromHash)

    fun locationPending(fromHash: String): JSONObject =
        base("loc_pending", fromHash)

    fun locationResponse(fromHash: String, encryptedPayload: String): JSONObject =
        base("loc_response", fromHash).apply {
            put("enc", encryptedPayload)
        }

    fun liveStart(
        fromHash: String,
        sessionId: String,
        durationMinutes: Int,
        intervalSeconds: Int
    ): JSONObject =
        base("live_start", fromHash).apply {
            put("session_id", sessionId)
            put("duration_min", durationMinutes)
            put("interval_sec", intervalSeconds)
        }

    fun livePoint(fromHash: String, encryptedPayload: String): JSONObject =
        base("live_point", fromHash).apply {
            put("enc", encryptedPayload)
        }

    fun liveStop(fromHash: String, sessionId: String): JSONObject =
        base("live_stop", fromHash).apply {
            put("session_id", sessionId)
        }

    private fun base(type: String, fromHash: String): JSONObject =
        JSONObject().apply {
            put("type", type)
            put("from", fromHash)
            put("ts", System.currentTimeMillis() / 1000)
        }
}
