package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertVolumePolicyTest {
    @Test fun messageVolumeUsesRequestedLowLevel() {
        assertEquals(1, AlertVolumePolicy.targetStreamVolume(15, 5))
    }

    @Test fun callVolumeLowersHigherAlarmLevel() {
        assertEquals(3, AlertVolumePolicy.targetStreamVolume(15, 25))
    }

    @Test fun requestedVolumeIsAppliedWhenHigherThanCurrent() {
        assertEquals(15, AlertVolumePolicy.targetStreamVolume(15, 100))
    }
}
