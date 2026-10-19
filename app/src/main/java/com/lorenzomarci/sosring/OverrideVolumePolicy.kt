package com.lorenzomarci.sosring

import android.media.AudioManager

data class OverrideAudioSnapshot(
    val ringerMode: Int,
    val ringVolume: Int,
    val ringMuted: Boolean,
    val alarmVolume: Int
)

enum class OverrideVolumeAction { NONE, SILENCE, RESTORE }

object OverrideVolumePolicy {
    fun decide(
        baseline: OverrideAudioSnapshot,
        current: OverrideAudioSnapshot,
        silenced: Boolean
    ): OverrideVolumeAction {
        if (!silenced) {
            val ringerChanged = current.ringerMode != baseline.ringerMode &&
                current.ringerMode != AudioManager.RINGER_MODE_NORMAL
            val ringMuted = current.ringMuted && !baseline.ringMuted
            val ringLowered = current.ringVolume < baseline.ringVolume
            val alarmLowered = current.alarmVolume < baseline.alarmVolume
            return if (ringerChanged || ringMuted || ringLowered || alarmLowered) {
                OverrideVolumeAction.SILENCE
            } else {
                OverrideVolumeAction.NONE
            }
        }

        val ringerRestored = baseline.ringerMode != AudioManager.RINGER_MODE_NORMAL &&
            current.ringerMode == AudioManager.RINGER_MODE_NORMAL
        val ringRaised = current.ringVolume > baseline.ringVolume
        val alarmRaised = current.alarmVolume > baseline.alarmVolume
        return if (ringerRestored || ringRaised || alarmRaised) {
            OverrideVolumeAction.RESTORE
        } else {
            OverrideVolumeAction.NONE
        }
    }
}
