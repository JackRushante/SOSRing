package com.lorenzomarci.sosring

import org.json.JSONArray
import org.json.JSONObject

enum class CallAlertMode { INHERIT, FIRST, SECOND;
    companion object {
        fun parse(value: String?, fallback: CallAlertMode = FIRST): CallAlertMode =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

data class CallAlertDecision(val shouldRing: Boolean, val volumePercent: Int, val exactVolume: Boolean)

object RepeatCallPolicy {
    val windowOptions = listOf(3, 5, 10)
    fun windowMinutes(value: Int): Int = value.takeIf { it in windowOptions } ?: 5

    fun decide(mode: CallAlertMode, attempt: Int, baseVolume: Int, escalate: Boolean): CallAlertDecision {
        val firstAudible = if (mode == CallAlertMode.SECOND) 2 else 1
        val base = baseVolume.coerceIn(25, 100)
        val progressive = escalate && base < 50
        var volume = base
        if (progressive) repeat((attempt - firstAudible).coerceIn(0, 2)) {
            volume = (volume * 2).coerceAtMost(100)
        }
        return CallAlertDecision(attempt >= firstAudible, volume, progressive)
    }
}

/** One aggregate cellular call session. Repeated broadcasts and call waiting are not new attempts. */
class RepeatCallTracker {
    private data class Attempt(val startedAt: Long, val count: Int)
    private val history = mutableMapOf<String, Attempt>()
    var busy = false
        private set
    private var ringing = false
    private var claimed = false
    private var activeKey: String? = null
    private var activeAttempt: Attempt? = null

    fun onRinging(
        key: String?, allowed: Boolean, now: Long, windowMs: Long,
        mode: CallAlertMode, baseVolume: Int, escalate: Boolean
    ): CallAlertDecision? {
        prune(now, windowMs)
        if (!busy) {
            busy = true
            ringing = true
            claimed = false
        }
        if (!ringing || claimed || key == null) return null
        claimed = true
        activeKey = key
        if (!allowed) {
            resetHistory()
            return null
        }
        val previous = history[key]
        val attempt = Attempt(previous?.startedAt ?: now, ((previous?.count ?: 0) + 1).coerceAtMost(4))
        activeAttempt = attempt
        return RepeatCallPolicy.decide(mode, attempt.count, baseVolume, escalate)
    }

    fun onAnswered() {
        activeKey?.let(history::remove)
        activeAttempt = null
        busy = true
        ringing = false
        claimed = true
    }

    fun onIdle() {
        val key = activeKey
        val attempt = activeAttempt
        if (ringing && key != null && attempt != null) history[key] = attempt
        busy = false
        ringing = false
        claimed = false
        activeKey = null
        activeAttempt = null
    }

    fun resetHistory() {
        history.clear()
        activeAttempt = null
        // Changing a setting or entering a pause cannot re-arm a call already in progress.
        if (busy) claimed = true
    }

    fun prune(now: Long, windowMs: Long) {
        history.entries.removeAll { now < it.value.startedAt || now - it.value.startedAt >= windowMs }
    }

    fun snapshot(): JSONObject = JSONObject().apply {
        put("history", JSONArray().apply {
            history.forEach { (key, attempt) ->
                put(JSONObject().put("key", key).put("start", attempt.startedAt).put("count", attempt.count))
            }
        })
        put("active", activeKey ?: JSONObject.NULL)
    }

    fun restore(snapshot: JSONObject, now: Long, windowMs: Long, phoneBusy: Boolean) {
        history.clear()
        val rows = snapshot.optJSONArray("history") ?: JSONArray()
        for (i in 0 until minOf(rows.length(), 1000)) {
            val row = rows.optJSONObject(i) ?: continue
            val key = row.optString("key")
            val count = row.optInt("count")
            val startedAt = row.optLong("start", -1)
            if (key.matches(Regex("[a-f0-9]{64}")) && count in 1..4 && startedAt >= 0) {
                history[key] = Attempt(startedAt, count)
            }
        }
        // If the process died during a call, its outcome is unknown: never infer a missed call.
        history.remove(snapshot.optString("active"))
        prune(now, windowMs)
        busy = phoneBusy
        ringing = phoneBusy
        claimed = phoneBusy
        activeKey = null
        activeAttempt = null
    }
}
