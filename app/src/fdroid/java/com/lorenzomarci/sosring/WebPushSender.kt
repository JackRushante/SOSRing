package com.lorenzomarci.sosring

import android.util.Log
import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a Web Push POST to a peer's push server did not go through. */
enum class PushFailure {
    /** 404/410: the push address in the pairing no longer exists (distributor re-registered). */
    STALE_PAIRING,
    /** 413: the push server refused the size of the message. */
    TOO_LARGE,
    /** 429: the push server is rate limiting this phone. */
    RATE_LIMITED,
    /** Any other refusal or a server error. */
    SERVER_ERROR,
    /** No answer at all: no internet connection, DNS or TLS failure, timeout. */
    NETWORK;

    companion object {
        fun fromStatus(code: Int?): PushFailure = when (code) {
            null -> NETWORK
            404, 410 -> STALE_PAIRING
            413 -> TOO_LARGE
            429 -> RATE_LIMITED
            else -> SERVER_ERROR
        }

        /** Retrying cannot help: the address is gone or the message will never fit. */
        fun isPermanent(code: Int): Boolean = code == 404 || code == 410 || code == 413
    }
}

object WebPushSender {

    private const val TAG = "WebPushSender"
    const val DEFAULT_TTL_SECONDS = 60

    /** Requests, answers and session control stay valid as long as the replay guard accepts them. */
    const val CONTROL_TTL_SECONDS = (P2pFreshness.SKEW_WINDOW_MS / 1000L).toInt()
    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    fun buildRequest(endpoint: String, body: ByteArray, ttlSeconds: Int = DEFAULT_TTL_SECONDS): Request {
        return Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(OCTET_STREAM))
            .header("TTL", ttlSeconds.toString())
            // RFC 8030: lets push servers deliver at once to a phone in Doze.
            .header("Urgency", "high")
            .header("Content-Encoding", "aes128gcm")
            .build()
    }

    /**
     * Posts in the background. [onFailure] runs on that thread once every attempt has failed,
     * [onSuccess] once the push server accepted the message.
     */
    fun send(
        endpoint: String,
        body: ByteArray,
        attempts: Int = 1,
        ttlSeconds: Int = DEFAULT_TTL_SECONDS,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((PushFailure) -> Unit)? = null
    ) {
        Thread {
            val total = attempts.coerceAtLeast(1)
            var lastCode: Int? = null
            for (attempt in 0 until total) {
                try {
                    Thread.sleep(ControlRetryPolicy.delayBeforeAttempt(attempt))
                    NetworkClient.client.newCall(buildRequest(endpoint, body, ttlSeconds)).execute().use { response ->
                        if (response.isSuccessful) {
                            onSuccess?.invoke()
                            return@Thread
                        }
                        lastCode = response.code
                        Log.e(TAG, "Web Push POST failed: ${response.code} (attempt ${attempt + 1}/$total)")
                    }
                    if (PushFailure.isPermanent(lastCode ?: 0)) break
                } catch (e: InterruptedException) {
                    return@Thread
                } catch (e: Exception) {
                    lastCode = null
                    Log.e(TAG, "Web Push POST error: ${e.message} (attempt ${attempt + 1}/$total)")
                }
            }
            onFailure?.invoke(PushFailure.fromStatus(lastCode))
        }.start()
    }
}
