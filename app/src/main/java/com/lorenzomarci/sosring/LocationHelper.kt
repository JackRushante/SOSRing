package com.lorenzomarci.sosring

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHelper(context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val ACCURACY_THRESHOLD = 30f // meters
        private const val TIMEOUT_MS = 15_000L
    }

    interface Callback {
        fun onLocationReady(location: Location)
        fun onLocationFailed()
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var bestLocation: Location? = null
    private var isRequesting = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            Log.d(TAG, "Fix: accuracy=${location.accuracy}m")

            val best = bestLocation
            if (best == null || location.accuracy < best.accuracy) {
                bestLocation = location
            }

            if (location.accuracy <= ACCURACY_THRESHOLD) {
                Log.i(TAG, "Good fix: ${location.accuracy}m <= ${ACCURACY_THRESHOLD}m")
                deliverAndStop(location)
            }
        }
    }

    private val timeoutRunnable = Runnable {
        if (isRequesting) {
            val best = bestLocation
            if (best != null) {
                Log.i(TAG, "Timeout, using best: ${best.accuracy}m")
                deliverAndStop(best)
            } else {
                Log.w(TAG, "Timeout, no fix obtained")
                stopUpdates()
                callback?.onLocationFailed()
                callback = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestSingleFix(cb: Callback) {
        if (isRequesting) return
        callback = cb
        bestLocation = null
        isRequesting = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdates(30)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    private fun deliverAndStop(location: Location) {
        stopUpdates()
        callback?.onLocationReady(location)
        callback = null
    }

    private fun stopUpdates() {
        isRequesting = false
        handler.removeCallbacks(timeoutRunnable)
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun stop() {
        stopUpdates()
        callback = null
    }
}
