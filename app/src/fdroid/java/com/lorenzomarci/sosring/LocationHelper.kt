package com.lorenzomarci.sosring

import android.content.Context
import android.location.Location

/** Stub for fdroid flavor — location sharing is disabled (LOCATION_ENABLED = false). */
class LocationHelper(context: Context) {

    interface Callback {
        fun onLocationReady(location: Location)
        fun onLocationFailed()
    }

    fun requestSingleFix(cb: Callback) {
        cb.onLocationFailed()
    }

    fun stop() {}
}
