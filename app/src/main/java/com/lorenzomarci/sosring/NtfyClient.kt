package com.lorenzomarci.sosring

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NtfyClient(private val serverUrl: String) {

    companion object {
        private const val TAG = "NtfyClient"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun sendMessage(topic: String, message: JSONObject) {
        Thread {
            try {
                val url = "$serverUrl/$topic"
                val body = message.toString().toRequestBody(JSON_TYPE)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "ntfy POST failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ntfy POST error: ${e.message}")
            }
        }.start()
    }

    fun sendDiscovery(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "discovery")
            put("from", fromHash)
        })
    }

    fun sendDiscoveryAck(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "discovery_ack")
            put("from", fromHash)
        })
    }

    fun sendLocationRequest(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_request")
            put("from", fromHash)
            put("ts", System.currentTimeMillis() / 1000)
        })
    }

    fun sendLocationPending(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_pending")
            put("from", fromHash)
            put("ts", System.currentTimeMillis() / 1000)
        })
    }

    fun sendLocationResponse(topic: String, fromHash: String, lat: Double, lon: Double, accuracy: Float) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_response")
            put("from", fromHash)
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy.toDouble())
            put("ts", System.currentTimeMillis() / 1000)
        })
    }
}
