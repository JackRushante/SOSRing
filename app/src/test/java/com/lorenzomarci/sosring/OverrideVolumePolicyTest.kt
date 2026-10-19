package com.lorenzomarci.sosring

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OverrideVolumePolicyTest {
    private val baseline = OverrideAudioSnapshot(
        ringerMode = AudioManager.RINGER_MODE_NORMAL,
        ringVolume = 5,
        ringMuted = false,
        alarmVolume = 5
    )

    @Test fun unchanged_system_doesNothing() =
        assertEquals(OverrideVolumeAction.NONE, OverrideVolumePolicy.decide(baseline, baseline, silenced = false))

    @Test fun ringerGoingSilent_silencesOverride() =
        assertEquals(
            OverrideVolumeAction.SILENCE,
            OverrideVolumePolicy.decide(
                baseline,
                baseline.copy(ringerMode = AudioManager.RINGER_MODE_SILENT),
                silenced = false
            )
        )

    @Test fun ringStreamMuted_silencesOverride() =
        assertEquals(
            OverrideVolumeAction.SILENCE,
            OverrideVolumePolicy.decide(baseline, baseline.copy(ringMuted = true), silenced = false)
        )

    @Test fun ringVolumeLowered_silencesOverride() =
        assertEquals(
            OverrideVolumeAction.SILENCE,
            OverrideVolumePolicy.decide(baseline, baseline.copy(ringVolume = 4), silenced = false)
        )

    @Test fun alarmVolumeLowered_silencesOverride() =
        assertEquals(
            OverrideVolumeAction.SILENCE,
            OverrideVolumePolicy.decide(baseline, baseline.copy(alarmVolume = 4), silenced = false)
        )

    @Test fun ringerBackToNormal_restoresSilencedOverride() =
        assertEquals(
            OverrideVolumeAction.RESTORE,
            OverrideVolumePolicy.decide(
                baseline.copy(ringerMode = AudioManager.RINGER_MODE_SILENT),
                baseline,
                silenced = true
            )
        )

    @Test fun ringVolumeRaised_restoresSilencedOverride() =
        assertEquals(
            OverrideVolumeAction.RESTORE,
            OverrideVolumePolicy.decide(baseline, baseline.copy(ringVolume = 6), silenced = true)
        )

    @Test fun alarmVolumeRaised_restoresSilencedOverride() =
        assertEquals(
            OverrideVolumeAction.RESTORE,
            OverrideVolumePolicy.decide(baseline, baseline.copy(alarmVolume = 6), silenced = true)
        )

    @Test fun alreadySilentWithoutChanges_doesNothing() {
        val silent = baseline.copy(ringerMode = AudioManager.RINGER_MODE_SILENT, ringVolume = 0)
        assertEquals(OverrideVolumeAction.NONE, OverrideVolumePolicy.decide(silent, silent, silenced = false))
    }

    @Test fun silencedWithoutChanges_doesNothing() =
        assertEquals(OverrideVolumeAction.NONE, OverrideVolumePolicy.decide(baseline, baseline, silenced = true))
}
