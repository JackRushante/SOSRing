package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthTest {
    @Test fun allGranted_noneMissing() {
        assertTrue(PermissionHealth.criticalMissing(callLogOk = true, phoneStateOk = true, dndOk = true).isEmpty())
    }
    @Test fun reportsEachMissing() {
        val missing = PermissionHealth.criticalMissing(callLogOk = false, phoneStateOk = true, dndOk = false)
        assertEquals(listOf("READ_CALL_LOG", "DND"), missing)
    }
}
