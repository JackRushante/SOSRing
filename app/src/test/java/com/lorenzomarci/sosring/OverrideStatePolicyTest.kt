package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideStatePolicyTest {

    @Test
    fun shouldRestoreWhenPersistedOverridingAndCallIsIdle() {
        assertTrue(OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding = true, callStateIdle = true))
    }

    @Test
    fun shouldNotRestoreWhenCallIsStillActive() {
        assertFalse(OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding = true, callStateIdle = false))
    }

    @Test
    fun shouldNotRestoreWhenNothingWasPersisted() {
        assertFalse(OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding = false, callStateIdle = true))
        assertFalse(OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding = false, callStateIdle = false))
    }
}
