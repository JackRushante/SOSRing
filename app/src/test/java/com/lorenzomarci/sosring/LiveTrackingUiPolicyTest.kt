package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTrackingUiPolicyTest {

    @Test
    fun durationPickerStartsAtOneMinute() {
        assertEquals(1, LiveTrackingUiPolicy.MIN_DURATION_MINUTES)
        assertEquals(15, LiveTrackingUiPolicy.DEFAULT_DURATION_MINUTES)
    }
}
