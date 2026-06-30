package com.lorenzomarci.sosring

import org.json.JSONObject

object P2pMessageFactory {

    const val TYPE_LOC_REQUEST = "pos_req"
    const val TYPE_LOC_RESPONSE = "pos_res"
    const val TYPE_LIVE_START = "liv_beg"
    const val TYPE_LIVE_POINT = "liv_pos"
    const val TYPE_LIVE_STOP = "liv_end"

    fun locRequest(now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().apply {
            put("type", TYPE_LOC_REQUEST)
            put("ts", now)
        }.toString().toByteArray(Charsets.UTF_8)

    fun locResponse(lat: Double, lon: Double, accuracy: Double, now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().apply {
            put("type", TYPE_LOC_RESPONSE)
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy)
            put("ts", now)
        }.toString().toByteArray(Charsets.UTF_8)

    fun type(payload: ByteArray): String? = try {
        JSONObject(String(payload, Charsets.UTF_8)).optString("type", "").ifBlank { null }
    } catch (e: Exception) {
        null
    }

    fun timestamp(payload: ByteArray): Long? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.has("ts")) json.getLong("ts") else null
    } catch (e: Exception) {
        null
    }

    data class LocResponse(val lat: Double, val lon: Double, val accuracy: Double)

    fun parseLocResponse(payload: ByteArray): LocResponse? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != TYPE_LOC_RESPONSE) {
            null
        } else {
            val lat = json.getDouble("lat")
            val lon = json.getDouble("lon")
            if (!lat.isFinite() || !lon.isFinite() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                null
            } else {
                LocResponse(lat, lon, json.optDouble("acc", 0.0))
            }
        }
    } catch (e: Exception) {
        null
    }

    fun liveStart(sessionId: String, durationMin: Int, intervalSec: Int, now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().apply {
            put("type", TYPE_LIVE_START)
            put("ts", now)
            put("sid", sessionId)
            put("dur", durationMin)
            put("int", intervalSec)
        }.toString().toByteArray(Charsets.UTF_8)

    fun livePoint(sessionId: String, lat: Double, lon: Double, accuracy: Double, now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().apply {
            put("type", TYPE_LIVE_POINT)
            put("ts", now)
            put("sid", sessionId)
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy)
        }.toString().toByteArray(Charsets.UTF_8)

    fun liveStop(sessionId: String, now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().apply {
            put("type", TYPE_LIVE_STOP)
            put("ts", now)
            put("sid", sessionId)
        }.toString().toByteArray(Charsets.UTF_8)

    data class LiveStart(val sessionId: String, val durationMin: Int, val intervalSec: Int)
    data class LivePoint(val sessionId: String, val lat: Double, val lon: Double, val accuracy: Double)
    data class LiveStop(val sessionId: String)

    fun parseLiveStart(payload: ByteArray): LiveStart? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != TYPE_LIVE_START) {
            null
        } else {
            val sid = json.optString("sid")
            if (sid.isBlank()) null
            else LiveStart(sid, json.optInt("dur", 15).coerceIn(1, 60), json.optInt("int", 10).coerceIn(5, 60))
        }
    } catch (e: Exception) {
        null
    }

    fun parseLivePoint(payload: ByteArray): LivePoint? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != TYPE_LIVE_POINT) {
            null
        } else {
            val sid = json.optString("sid")
            val lat = json.getDouble("lat")
            val lon = json.getDouble("lon")
            if (sid.isBlank() || !lat.isFinite() || !lon.isFinite() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                null
            } else {
                LivePoint(sid, lat, lon, json.optDouble("acc", 0.0))
            }
        }
    } catch (e: Exception) {
        null
    }

    fun parseLiveStop(payload: ByteArray): LiveStop? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != TYPE_LIVE_STOP) {
            null
        } else {
            val sid = json.optString("sid")
            if (sid.isBlank()) null else LiveStop(sid)
        }
    } catch (e: Exception) {
        null
    }
}
