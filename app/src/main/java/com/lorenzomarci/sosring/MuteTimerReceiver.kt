package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MuteTimerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UNMUTE = "com.lorenzomarci.sosring.ACTION_UNMUTE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UNMUTE) {
            val prefs = PrefsManager(context)
            prefs.muteUntilTimestamp = 0L
            Log.i("SOSRing", "Mute timer expired. Ringtone override re-enabled.")
        }
    }
}
