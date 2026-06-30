package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarnThrottleTest {
    @Test fun warns_afterInterval() = assertTrue(WarnThrottle.shouldWarn(0L, 24L * 60 * 60 * 1000))
    @Test fun throttled_withinInterval() = assertFalse(WarnThrottle.shouldWarn(0L, 1000L))
}
