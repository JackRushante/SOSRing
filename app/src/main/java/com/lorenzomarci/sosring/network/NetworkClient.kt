package com.lorenzomarci.sosring.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object NetworkClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Client dedicato alle subscription SSE: il readTimeout deve superare il
    // keepalive di ntfy (45s), altrimenti la connessione cicla in timeout ogni
    // 30s e ogni buco di riconnessione può perdere messaggi.
    val sseClient: OkHttpClient = client.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
}
