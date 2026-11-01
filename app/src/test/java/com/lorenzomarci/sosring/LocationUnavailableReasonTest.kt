package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationUnavailableReasonTest {

    @Test
    fun codesRoundTrip() {
        for (reason in LocationUnavailableReason.entries) {
            assertEquals(reason, LocationUnavailableReason.fromCode(reason.code))
        }
    }

    @Test
    fun wireCodesAreStable() {
        assertEquals("location_off", LocationUnavailableReason.LOCATION_OFF.code)
        assertEquals("no_fix", LocationUnavailableReason.NO_FIX.code)
        assertEquals("no_permission", LocationUnavailableReason.NO_BACKGROUND_PERMISSION.code)
        assertEquals("service_inactive", LocationUnavailableReason.SERVICE_INACTIVE.code)
    }

    @Test
    fun unknownOrMissingCodeIsNull() {
        assertNull(LocationUnavailableReason.fromCode("battery_saver"))
        assertNull(LocationUnavailableReason.fromCode(""))
        assertNull(LocationUnavailableReason.fromCode(null))
    }

    @Test
    fun failedFixWithLocationOffBlamesTheSwitch() {
        assertEquals(LocationUnavailableReason.LOCATION_OFF, LocationUnavailableReason.afterFailedFix(false))
        assertEquals(LocationUnavailableReason.NO_FIX, LocationUnavailableReason.afterFailedFix(true))
    }
}
