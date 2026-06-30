package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class P2pLiveStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            P2pLiveController.stopIncoming(context.applicationContext, sendEnd = true)
        }
    }

    companion object {
        const val ACTION_STOP = "com.lorenzomarci.sosring.LIVE_STOP"
    }
}
