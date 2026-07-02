package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class VipRowIconsTest {
    @Test
    fun cannotRequest_showsNothing() {
        val r = VipRowIcons.rowIcons(canRequest = false, locationEnabled = true, isLiveForThisContact = false)
        assertEquals(VipRowIcons(false, false, false), r)
    }

    @Test
    fun canRequestIdle_showsGpsOnly_regardlessOfLocationEnabled() {
        val r = VipRowIcons.rowIcons(canRequest = true, locationEnabled = false, isLiveForThisContact = false)
        assertEquals(VipRowIcons(showGps = true, showStop = false, showMap = false), r)
    }

    @Test
    fun canRequestLive_showsStopAndMap() {
        val r = VipRowIcons.rowIcons(canRequest = true, locationEnabled = true, isLiveForThisContact = true)
        assertEquals(VipRowIcons(showGps = false, showStop = true, showMap = true), r)
    }
}
