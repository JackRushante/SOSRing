package com.lorenzomarci.sosring

import android.app.NotificationManager
import android.media.AudioManager

object AudioOverridePolicy {
    fun shouldOverride(ringerMode: Int, ringStreamVolume: Int, interruptionFilter: Int): Boolean {
        val ringerAudible = ringerMode == AudioManager.RINGER_MODE_NORMAL && ringStreamVolume > 0
        val dndAllowsAll = interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        return !ringerAudible || !dndAllowsAll
    }
}
