package com.lorenzomarci.sosring

import android.util.Log
import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WebPushSender {

    private const val TAG = "WebPushSender"
    private const val DEFAULT_TTL_SECONDS = 60
    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    fun buildRequest(endpoint: String, body: ByteArray, ttlSeconds: Int = DEFAULT_TTL_SECONDS): Request {
        return Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(OCTET_STREAM))
            .header("TTL", ttlSeconds.toString())
            .header("Content-Encoding", "aes128gcm")
            .build()
    }

    fun send(endpoint: String, body: ByteArray) {
        Thread {
            try {
                NetworkClient.client.newCall(buildRequest(endpoint, body)).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Web Push POST failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Web Push POST error: ${e.message}")
            }
        }.start()
    }
}
