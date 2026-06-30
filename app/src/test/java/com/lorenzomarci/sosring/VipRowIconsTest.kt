package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class VipRowIconsTest {
    @Test
    fun notPaired_showsNothing() {
        val r = VipRowIcons.rowIcons(locationEnabled = false, isLiveForThisContact = false)
        assertEquals(VipRowIcons(false, false, false), r)
    }

    @Test
    fun pairedIdle_showsGpsOnly() {
        val r = VipRowIcons.rowIcons(locationEnabled = true, isLiveForThisContact = false)
        assertEquals(VipRowIcons(showGps = true, showStop = false, showMap = false), r)
    }

    @Test
    fun pairedLive_showsStopAndMap() {
        val r = VipRowIcons.rowIcons(locationEnabled = true, isLiveForThisContact = true)
        assertEquals(VipRowIcons(showGps = false, showStop = true, showMap = true), r)
    }
}
