package com.lorenzomarci.sosring

import org.json.JSONObject

data class PairingPayload(val passphrase: String, val token: String?)

object PairingCodec {
    fun encode(passphrase: String, token: String?): String {
        val obj = JSONObject()
        obj.put("p", passphrase)
        if (!token.isNullOrBlank()) obj.put("t", token)
        return obj.toString()
    }

    fun decode(raw: String): PairingPayload? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("{")) {
            return try {
                val obj = JSONObject(s)
                val p = obj.optString("p", "")
                if (p.isBlank()) return null
                val t = obj.optString("t", "").ifBlank { null }
                PairingPayload(p, t)
            } catch (e: Exception) {
                null
            }
        }
        return PairingPayload(s, null)
    }
}
