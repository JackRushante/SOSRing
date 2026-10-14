package com.lorenzomarci.sosring

object AlertVolumePolicy {
    fun targetStreamVolume(maxVolume: Int, percent: Int): Int =
        (maxVolume * percent.coerceIn(1, 100) / 100).coerceAtLeast(1)
}
