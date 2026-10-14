package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CallVolumeRampPolicyTest {
    private val linearDb = { index: Int -> -3f * (16 - index) }

    @Test fun defaultsAreTenToEighty() {
        val ramp = CallVolumeRampPolicy.normalize(
            CallVolumeRampPolicy.DEFAULT_START_PERCENT, CallVolumeRampPolicy.DEFAULT_END_PERCENT
        )
        assertEquals(CallVolumeRamp(10, 80), ramp)
    }

    @Test fun normalizeClampsToRange() {
        assertEquals(CallVolumeRamp(5, 100), CallVolumeRampPolicy.normalize(0, 200))
    }

    @Test fun normalizeKeepsEndAboveStart() {
        assertEquals(CallVolumeRamp(90, 95), CallVolumeRampPolicy.normalize(90, 50))
        assertEquals(CallVolumeRamp(95, 100), CallVolumeRampPolicy.normalize(100, 100))
    }

    @Test fun normalizeSnapsToSliderStep() {
        assertEquals(CallVolumeRamp(10, 85), CallVolumeRampPolicy.normalize(12, 83))
    }

    @Test fun freshInstallUsesFlavorDefaultWithoutPrompt() {
        assertEquals(CallRampSetup(enabled = true, prompt = false),
            CallVolumeRampPolicy.initialSetup(existingInstall = false, defaultEnabled = true))
        assertEquals(CallRampSetup(enabled = false, prompt = false),
            CallVolumeRampPolicy.initialSetup(existingInstall = false, defaultEnabled = false))
    }

    @Test fun updateKeepsInstantVolumeAndPromptsOnlyWhereDefaultIsRamp() {
        assertEquals(CallRampSetup(enabled = false, prompt = true),
            CallVolumeRampPolicy.initialSetup(existingInstall = true, defaultEnabled = true))
        assertEquals(CallRampSetup(enabled = false, prompt = false),
            CallVolumeRampPolicy.initialSetup(existingInstall = true, defaultEnabled = false))
    }

    @Test fun curveStartsAtStartPositionAndEndsAtFullGain() {
        val curve = CallVolumeRampPolicy.curve(10, maxIndex = 16, endIndex = 12, measureDb = linearDb)
        assertEquals(10f.pow((-43.2f + 12f) / 20f), curve.gainAt(0), 0.0005f)
        assertEquals(10f.pow((-27.6f + 12f) / 20f), curve.gainAt(5_000), 0.0005f)
        assertEquals(1f, curve.gainAt(CallVolumeRampPolicy.DURATION_MS), 0f)
        assertEquals(1f, curve.gainAt(60_000), 0f)
    }

    @Test fun curveRisesMonotonically() {
        val curve = CallVolumeRampPolicy.curve(10, maxIndex = 16, endIndex = 12, measureDb = linearDb)
        var previous = 0f
        for (ms in 0..CallVolumeRampPolicy.DURATION_MS step CallVolumeRampPolicy.TICK_MS) {
            val gain = curve.gainAt(ms)
            assertTrue("gain decreased at $ms ms", gain >= previous)
            previous = gain
        }
    }

    @Test fun startBelowLowestStepUsesLowestStep() {
        val curve = CallVolumeRampPolicy.curve(5, maxIndex = 7, endIndex = 5) { index -> -6f * (7 - index) }
        assertEquals(10f.pow((-36f + 12f) / 20f), curve.gainAt(0), 0.0005f)
    }

    @Test fun startAtOrAboveEndStepIsFlat() {
        val curve = CallVolumeRampPolicy.curve(75, maxIndex = 7, endIndex = 5) { index -> -6f * (7 - index) }
        assertEquals(1f, curve.gainAt(0), 0f)
        assertFalse(curve.ramps)
    }

    @Test fun invalidMeasurementsFallBackToDefaultCurve() {
        val fallback = CallVolumeRampPolicy.dbTable(16) { null }
        assertEquals(fallback.toList(), CallVolumeRampPolicy.dbTable(16) { Float.NaN }.toList())
        assertEquals(fallback.toList(), CallVolumeRampPolicy.dbTable(16) { index -> if (index == 8) -90f else -2f * (16 - index) }.toList())
        assertEquals(0f, fallback[16], 0f)
        for (i in 2..16) assertTrue(fallback[i] > fallback[i - 1])
    }

    @Test fun validMeasurementsAreUsed() {
        val table = CallVolumeRampPolicy.dbTable(16, linearDb)
        assertEquals(-45f, table[1], 0f)
        assertEquals(-12f, table[12], 0f)
    }
}
