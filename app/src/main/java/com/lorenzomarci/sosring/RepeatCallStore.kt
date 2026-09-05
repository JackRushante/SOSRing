package com.lorenzomarci.sosring

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs

class RepeatCallStore(context: Context, private val prefs: PrefsManager, phoneBusy: Boolean) {
    private val storage = context.getSharedPreferences("sosring_call_attempts", Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val tracker = RepeatCallTracker()
    private var checkedElapsed = SystemClock.elapsedRealtime()
    private var checkedWall = System.currentTimeMillis()
    val busy: Boolean get() = tracker.busy
    private val windowMs: Long get() = prefs.repeatCallWindowMinutes * 60_000L

    init {
        val saved = runCatching { JSONObject(storage.getString("state", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (boot >= 0 && saved.optInt("boot", -2) == boot) {
            checkedElapsed = saved.optLong("elapsed", checkedElapsed)
            checkedWall = saved.optLong("wall", checkedWall)
            tracker.restore(saved, SystemClock.elapsedRealtime(), windowMs, phoneBusy)
        } else {
            tracker.restore(JSONObject(), checkedElapsed, windowMs, phoneBusy)
        }
        checkGates()
        save()
    }

    fun onRinging(contact: VipContact?): CallAlertDecision? {
        val allowed = checkGates()
        val mode = contact?.callAlertMode?.takeUnless { it == CallAlertMode.INHERIT } ?: prefs.callAlertMode
        val key = contact?.let {
            MessageDigest.getInstance("SHA-256").digest(PhoneUtils.normalize(it.number).toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        val result = tracker.onRinging(key, allowed, checkedElapsed, windowMs, mode, prefs.volumePercent, prefs.escalateCallVolume)
        save()
        return result
    }

    fun onAnswered() { checkGates(); tracker.onAnswered(); save() }
    fun onIdle() { checkGates(); tracker.onIdle(); save() }
    fun reset() { tracker.resetHistory(); save() }

    private fun checkGates(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val allowed = AlertGatePolicy.allowsCall(prefs.isServiceEnabled, prefs.isMuted, prefs.isInQuietPeriod())
        val clockChanged = now < checkedElapsed || abs((wall - checkedWall) - (now - checkedElapsed)) > 2_000L
        if (!allowed || clockChanged || now - checkedElapsed >= windowMs ||
            QuietHoursPolicy.wasQuietBetween(prefs.getQuietRules(), checkedWall, wall)) tracker.resetHistory()
        tracker.prune(now, windowMs)
        checkedElapsed = now
        checkedWall = wall
        return allowed
    }

    private fun save() {
        storage.edit().putString("state", tracker.snapshot().put("boot", boot)
            .put("elapsed", checkedElapsed).put("wall", checkedWall).toString()).apply()
    }
}
