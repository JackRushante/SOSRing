package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertGatePolicyTest {
    @Test fun activeMonitoringAllowsBothAlerts() {
        assertTrue(AlertGatePolicy.allowsCall(true, false, false))
        assertTrue(AlertGatePolicy.allowsMessage(true, false, false, true))
    }

    @Test fun monitoringOffBlocksBothAlerts() {
        assertFalse(AlertGatePolicy.allowsCall(false, false, false))
        assertFalse(AlertGatePolicy.allowsMessage(false, false, false, true))
    }

    @Test fun presetPauseBlocksBothAlerts() {
        assertFalse(AlertGatePolicy.allowsCall(true, true, false))
        assertFalse(AlertGatePolicy.allowsMessage(true, true, false, true))
    }

    @Test fun scheduledQuietHoursBlockBothAlerts() {
        assertFalse(AlertGatePolicy.allowsCall(true, false, true))
        assertFalse(AlertGatePolicy.allowsMessage(true, false, true, true))
    }

    @Test fun messageToggleOnlyBlocksMessageSound() {
        assertTrue(AlertGatePolicy.allowsCall(true, false, false))
        assertFalse(AlertGatePolicy.allowsMessage(true, false, false, false))
    }
}
