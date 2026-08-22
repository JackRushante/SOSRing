package com.lorenzomarci.sosring

object AlertVolumePolicy {
    fun targetStreamVolume(
        maxVolume: Int,
        currentVolume: Int,
        percent: Int,
        preserveHigherCurrentVolume: Boolean
    ): Int {
        val requested = (maxVolume * percent.coerceIn(1, 100) / 100).coerceAtLeast(1)
        return if (preserveHigherCurrentVolume) maxOf(requested, currentVolume) else requested
    }
}
