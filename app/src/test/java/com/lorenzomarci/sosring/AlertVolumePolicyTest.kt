package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertVolumePolicyTest {
    @Test fun messageVolumeUsesRequestedLowLevel() {
        assertEquals(1, AlertVolumePolicy.targetStreamVolume(15, 12, 5, false))
    }

    @Test fun callVolumeStillPreservesHigherAlarmLevel() {
        assertEquals(12, AlertVolumePolicy.targetStreamVolume(15, 12, 25, true))
    }

    @Test fun requestedVolumeIsAppliedWhenHigherThanCurrent() {
        assertEquals(15, AlertVolumePolicy.targetStreamVolume(15, 2, 100, false))
    }
}
