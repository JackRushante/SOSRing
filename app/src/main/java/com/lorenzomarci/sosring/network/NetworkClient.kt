package com.lorenzomarci.sosring.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object NetworkClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
}
