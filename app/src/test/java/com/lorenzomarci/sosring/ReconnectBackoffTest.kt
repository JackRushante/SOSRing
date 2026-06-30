package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test fun firstAttempt_isBase() = assertEquals(2000L, ReconnectBackoff.delayMs(0))
    @Test fun grows_exponentially() {
        assertEquals(4000L, ReconnectBackoff.delayMs(1))
        assertEquals(8000L, ReconnectBackoff.delayMs(2))
    }
    @Test fun caps_at_max() = assertEquals(60000L, ReconnectBackoff.delayMs(20))
}
