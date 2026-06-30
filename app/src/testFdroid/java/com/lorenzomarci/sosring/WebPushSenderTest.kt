package com.lorenzomarci.sosring

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPushSenderTest {

    @Test
    fun buildRequest_setsWebPushHeadersAndBody() {
        val body = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val request = WebPushSender.buildRequest(
            endpoint = "https://push.example.com/upABC123",
            body = body,
            ttlSeconds = 90
        )

        assertEquals("POST", request.method)
        assertEquals("https://push.example.com/upABC123", request.url.toString())
        assertEquals("90", request.header("TTL"))
        assertEquals("aes128gcm", request.header("Content-Encoding"))

        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertArrayEquals(body, buffer.readByteArray())
    }

    @Test
    fun buildRequest_defaultTtlIs60() {
        val request = WebPushSender.buildRequest(
            endpoint = "https://push.example.com/up1",
            body = byteArrayOf(0x00)
        )

        assertEquals("60", request.header("TTL"))
    }
}
