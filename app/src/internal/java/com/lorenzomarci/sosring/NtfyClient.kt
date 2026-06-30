package com.lorenzomarci.sosring

import android.content.Context
import android.util.Log
import com.lorenzomarci.sosring.network.NetworkClient
import org.json.JSONObject

class NtfyClient(private val context: Context, private val serverUrl: String) {

    companion object {
        private const val TAG = "NtfyClient"
    }

    private val prefs = PrefsManager(context)

    fun sendMessage(topic: String, message: JSONObject) {
        Thread {
            try {
                val request = NtfyRequestFactory.publish(
                    serverUrl = serverUrl,
                    topic = topic,
                    message = message,
                    token = prefs.ntfyAuthToken
                )
                NetworkClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "ntfy POST failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ntfy POST error: ${e.message}")
            }
        }.start()
    }

    fun sendDiscovery(topic: String, fromHash: String, keyFingerprint: String? = null) {
        sendMessage(topic, NtfyMessageFactory.discovery(fromHash, keyFingerprint))
    }

    fun sendDiscoveryAck(topic: String, fromHash: String, keyFingerprint: String? = null) {
        sendMessage(topic, NtfyMessageFactory.discoveryAck(fromHash, keyFingerprint))
    }

    fun sendKeyRotated(topic: String, fromHash: String, keyFingerprint: String?) {
        sendMessage(topic, NtfyMessageFactory.keyRotated(fromHash, keyFingerprint))
    }

    fun sendLocationRequest(topic: String, fromHash: String) {
        sendMessage(topic, NtfyMessageFactory.locationRequest(fromHash))
    }

    fun sendLocationPending(topic: String, fromHash: String) {
        sendMessage(topic, NtfyMessageFactory.locationPending(fromHash))
    }

    fun sendLocationResponse(topic: String, fromHash: String, lat: Double, lon: Double, accuracy: Float,
                             myNumber: String, theirNumber: String) {
        val locationJson = JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy.toDouble())
        }.toString()
        val encrypted = CryptoHelper.encrypt(locationJson, myNumber, theirNumber)
        sendMessage(topic, NtfyMessageFactory.locationResponse(fromHash, encrypted))
    }

    fun sendLiveStart(topic: String, fromHash: String, sessionId: String, durationMinutes: Int, intervalSeconds: Int) {
        sendMessage(
            topic,
            NtfyMessageFactory.liveStart(fromHash, sessionId, durationMinutes, intervalSeconds)
        )
    }

    fun sendLivePoint(
        topic: String,
        fromHash: String,
        sessionId: String,
        lat: Double,
        lon: Double,
        accuracy: Float,
        myNumber: String,
        theirNumber: String
    ) {
        val locationJson = JSONObject().apply {
            put("session_id", sessionId)
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy.toDouble())
        }.toString()
        val encrypted = CryptoHelper.encrypt(locationJson, myNumber, theirNumber)
        sendMessage(topic, NtfyMessageFactory.livePoint(fromHash, encrypted))
    }

    fun sendLiveStop(topic: String, fromHash: String, sessionId: String) {
        sendMessage(topic, NtfyMessageFactory.liveStop(fromHash, sessionId))
    }
}
