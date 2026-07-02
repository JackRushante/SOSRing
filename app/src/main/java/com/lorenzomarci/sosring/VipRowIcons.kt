package com.lorenzomarci.sosring

data class VipRowIcons(
    val showGps: Boolean,
    val showStop: Boolean,
    val showMap: Boolean
) {
    companion object {
        fun rowIcons(canRequest: Boolean, locationEnabled: Boolean, isLiveForThisContact: Boolean): VipRowIcons {
            return when {
                !canRequest -> VipRowIcons(false, false, false)
                isLiveForThisContact -> VipRowIcons(showGps = false, showStop = true, showMap = true)
                else -> VipRowIcons(showGps = true, showStop = false, showMap = false)
            }
        }
    }
}
