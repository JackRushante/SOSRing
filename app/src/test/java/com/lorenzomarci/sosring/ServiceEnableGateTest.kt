package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceEnableGateTest {

    @Test fun allFourGranted_canEnable() {
        assertTrue(ServiceEnableGate.canEnable(phone = true, callLog = true, dnd = true, notif = true))
    }

    @Test fun phoneMissing_cannotEnable() {
        assertFalse(ServiceEnableGate.canEnable(phone = false, callLog = true, dnd = true, notif = true))
    }

    @Test fun callLogMissing_cannotEnable() {
        assertFalse(ServiceEnableGate.canEnable(phone = true, callLog = false, dnd = true, notif = true))
    }

    @Test fun dndMissing_cannotEnable() {
        assertFalse(ServiceEnableGate.canEnable(phone = true, callLog = true, dnd = false, notif = true))
    }

    @Test fun notifMissing_cannotEnable() {
        assertFalse(ServiceEnableGate.canEnable(phone = true, callLog = true, dnd = true, notif = false))
    }

    @Test fun allFourMissing_cannotEnable() {
        assertFalse(ServiceEnableGate.canEnable(phone = false, callLog = false, dnd = false, notif = false))
    }
}
