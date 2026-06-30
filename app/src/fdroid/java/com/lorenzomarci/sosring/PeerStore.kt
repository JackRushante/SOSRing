package com.lorenzomarci.sosring

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Peer(
    val number: String,
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val idPub: String
)

class PeerStore(context: Context) {

    private val prefs = context.getSharedPreferences("sosring_peers", Context.MODE_PRIVATE)

    fun all(): List<Peer> {
        val json = prefs.getString(KEY_PEERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Peer(
                    number = obj.getString("number"),
                    endpoint = obj.getString("endpoint"),
                    p256dh = obj.getString("p256dh"),
                    auth = obj.getString("auth"),
                    idPub = obj.getString("idPub")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(number: String): Peer? {
        val target = PhoneUtils.normalize(number)
        return all().firstOrNull { PhoneUtils.normalize(it.number) == target }
    }

    fun save(peer: Peer) {
        val normalized = peer.copy(number = PhoneUtils.normalize(peer.number))
        val target = normalized.number
        persist(all().filter { PhoneUtils.normalize(it.number) != target } + normalized)
    }

    fun remove(number: String) {
        val target = PhoneUtils.normalize(number)
        persist(all().filter { PhoneUtils.normalize(it.number) != target })
    }

    fun isTrusted(idPub: ByteArray): Boolean {
        val target = WebPushCrypto.b64enc(idPub)
        return all().any { it.idPub == target }
    }

    fun byIdPub(idPub: ByteArray): Peer? {
        val target = WebPushCrypto.b64enc(idPub)
        return all().firstOrNull { it.idPub == target }
    }

    private fun persist(peers: List<Peer>) {
        val arr = JSONArray()
        peers.forEach { peer ->
            arr.put(JSONObject().apply {
                put("number", peer.number)
                put("endpoint", peer.endpoint)
                put("p256dh", peer.p256dh)
                put("auth", peer.auth)
                put("idPub", peer.idPub)
            })
        }
        prefs.edit().putString(KEY_PEERS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_PEERS = "peers"
    }
}
