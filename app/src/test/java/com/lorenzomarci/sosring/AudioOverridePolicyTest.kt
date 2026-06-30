package com.lorenzomarci.sosring

import android.app.NotificationManager
import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOverridePolicyTest {

    private val DEFAULT_VOLUME = 7

    @Test
    fun shouldOverrideWhenRingerNormalButDndEnabled() {
        assertTrue(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                ringStreamVolume = DEFAULT_VOLUME,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        )
    }

    @Test
    fun shouldSkipWhenRingerNormalVolumePositiveAndDndAll() {
        assertFalse(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                ringStreamVolume = DEFAULT_VOLUME,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
    }

    @Test
    fun shouldOverrideSilentAndVibrateRegardlessOfDndFilter() {
        assertTrue(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_SILENT,
                ringStreamVolume = DEFAULT_VOLUME,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
        assertTrue(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_VIBRATE,
                ringStreamVolume = DEFAULT_VOLUME,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
    }

    @Test
    fun shouldOverrideWhenRingerNormalButVolumeZero() {
        assertTrue(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                ringStreamVolume = 0,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
    }

    @Test
    fun shouldOverrideWhenDndFilterUnknown() {
        assertTrue(
            AudioOverridePolicy.shouldOverride(
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
                ringStreamVolume = DEFAULT_VOLUME,
                interruptionFilter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            )
        )
    }
}
