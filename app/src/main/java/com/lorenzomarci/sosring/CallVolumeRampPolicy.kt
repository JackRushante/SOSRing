package com.lorenzomarci.sosring

import kotlin.math.floor
import kotlin.math.pow

data class CallVolumeRamp(val startPercent: Int, val endPercent: Int)

data class CallRampSetup(val enabled: Boolean, val prompt: Boolean)

class CallVolumeRampCurve(startPercent: Int, maxIndex: Int, private val endIndex: Int, private val dbByIndex: FloatArray) {
    private val startPosition = (startPercent * maxIndex / 100f).coerceIn(1f, endIndex.toFloat())
    val ramps: Boolean = startPosition < endIndex

    fun gainAt(elapsedMs: Long): Float {
        if (!ramps || elapsedMs >= CallVolumeRampPolicy.DURATION_MS) return 1f
        val progress = elapsedMs.coerceAtLeast(0L) / CallVolumeRampPolicy.DURATION_MS.toFloat()
        val position = startPosition + (endIndex - startPosition) * progress
        val low = floor(position).toInt()
        val high = minOf(low + 1, endIndex)
        val db = dbByIndex[low] + (dbByIndex[high] - dbByIndex[low]) * (position - low)
        return 10f.pow((db - dbByIndex[endIndex]) / 20f).coerceIn(0f, 1f)
    }
}

object CallVolumeRampPolicy {
    const val DURATION_MS = 10_000L
    const val TICK_MS = 100L
    const val MIN_PERCENT = 5
    const val MAX_PERCENT = 100
    const val STEP_PERCENT = 5
    const val DEFAULT_START_PERCENT = 10
    const val DEFAULT_END_PERCENT = 80

    private val fallbackCurve = listOf(0f to -58f, 20f to -40f, 60f to -17f, 100f to 0f)

    fun normalize(startPercent: Int, endPercent: Int): CallVolumeRamp {
        val start = snap(startPercent).coerceIn(MIN_PERCENT, MAX_PERCENT - STEP_PERCENT)
        return CallVolumeRamp(start, snap(endPercent).coerceIn(start + STEP_PERCENT, MAX_PERCENT))
    }

    private fun snap(percent: Int): Int = (percent.coerceIn(0, MAX_PERCENT) + STEP_PERCENT / 2) / STEP_PERCENT * STEP_PERCENT

    fun initialSetup(existingInstall: Boolean, defaultEnabled: Boolean): CallRampSetup =
        if (existingInstall) CallRampSetup(enabled = false, prompt = defaultEnabled)
        else CallRampSetup(enabled = defaultEnabled, prompt = false)

    fun dbTable(maxIndex: Int, measureDb: (Int) -> Float?): FloatArray {
        val measured = (1..maxIndex).map(measureDb)
        val valid = measured.all { it != null && it.isFinite() } &&
            measured.zipWithNext().all { (a, b) -> b!! >= a!! }
        val values = if (valid) measured.map { it!! } else (1..maxIndex).map { fallbackDb(it * 100f / maxIndex) }
        return FloatArray(maxIndex + 1) { index -> values[(index - 1).coerceAtLeast(0)] }
    }

    fun curve(startPercent: Int, maxIndex: Int, endIndex: Int, measureDb: (Int) -> Float?): CallVolumeRampCurve {
        val max = maxIndex.coerceAtLeast(1)
        return CallVolumeRampCurve(startPercent, max, endIndex.coerceIn(1, max), dbTable(max, measureDb))
    }

    private fun fallbackDb(percent: Float): Float {
        val (to, toDb) = fallbackCurve.first { it.first >= percent }
        val (from, fromDb) = fallbackCurve.last { it.first <= percent }
        if (to == from) return toDb
        return fromDb + (toDb - fromDb) * (percent - from) / (to - from)
    }
}
