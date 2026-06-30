package com.lorenzomarci.sosring

import org.json.JSONObject

object P2pEnvelope {

    private const val VERSION = 1

    fun seal(payload: ByteArray, signerIdPub: ByteArray, sign: (ByteArray) -> ByteArray): ByteArray {
        val signature = sign(payload)
        val json = JSONObject().apply {
            put("v", VERSION)
            put("p", WebPushCrypto.b64enc(payload))
            put("k", WebPushCrypto.b64enc(signerIdPub))
            put("s", WebPushCrypto.b64enc(signature))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    data class Opened(val payload: ByteArray, val senderIdPub: ByteArray)

    fun open(envelope: ByteArray, isTrusted: (ByteArray) -> Boolean): Opened? {
        return try {
            val json = JSONObject(String(envelope, Charsets.UTF_8))
            if (json.optInt("v", -1) != VERSION) return null
            val payload = WebPushCrypto.b64dec(json.getString("p"))
            val idPub = WebPushCrypto.b64dec(json.getString("k"))
            val signature = WebPushCrypto.b64dec(json.getString("s"))
            if (!isTrusted(idPub)) return null
            if (!MessageAuth.verify(idPub, payload, signature)) return null
            Opened(payload, idPub)
        } catch (e: Exception) {
            null
        }
    }
}
