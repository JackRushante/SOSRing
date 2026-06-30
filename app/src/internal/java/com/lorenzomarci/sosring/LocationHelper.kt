package com.lorenzomarci.sosring

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val ACCURACY_THRESHOLD = 30f
        private const val TIMEOUT_MS = 15_000L
    }

    interface Callback {
        fun onLocationReady(location: Location)
        fun onLocationFailed()
    }

    interface LiveCallback {
        fun onLocationUpdate(location: Location)
        fun onLiveError(message: String)
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var bestLocation: Location? = null
    private var isRequesting = false

    private var liveCallback: LiveCallback? = null
    private var isLiveTracking = false

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleFix(it, "Fused") }
        }
    }

    private val liveFusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { liveCallback?.onLocationUpdate(it) }
        }
    }

    private fun handleFix(location: Location, source: String) {
        Log.d(TAG, "Fix from $source: accuracy=${location.accuracy}m")
        val best = bestLocation
        if (best == null || location.accuracy < best.accuracy) {
            bestLocation = location
        }
        if (location.accuracy <= ACCURACY_THRESHOLD) {
            Log.i(TAG, "Good fix from $source: ${location.accuracy}m")
            deliverAndStop(location)
        }
    }

    private val timeoutRunnable = Runnable {
        if (isRequesting) {
            val best = bestLocation
            if (best != null) {
                Log.i(TAG, "Timeout, using best: ${best.accuracy}m from ${best.provider}")
                deliverAndStop(best)
            } else {
                val lastKnown = getLastKnownLocation()
                if (lastKnown != null) {
                    Log.i(TAG, "Using last known: ${lastKnown.accuracy}m from ${lastKnown.provider}")
                    deliverAndStop(lastKnown)
                } else {
                    Log.w(TAG, "Timeout, no fix obtained from any source")
                    stopUpdates()
                    callback?.onLocationFailed()
                    callback = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestSingleFix(cb: Callback) {
        if (isRequesting) return
        if (!hasForegroundLocationPermission()) {
            cb.onLocationFailed()
            return
        }

        callback = cb
        bestLocation = null
        isRequesting = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdates(30)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Fused permission error: ${e.message}")
        }

        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val now = System.currentTimeMillis()
        var best: Location? = null
        for (provider in locationManager.getProviders(true)) {
            try {
                @Suppress("DEPRECATION")
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null &&
                    LocationFreshness.isFresh(loc.time, now) &&
                    (best == null || loc.accuracy < best.accuracy)
                ) {
                    best = loc
                }
            } catch (e: SecurityException) {
            }
        }
        return best
    }

    @SuppressLint("MissingPermission")
    fun startLiveTracking(
        cb: LiveCallback,
        intervalMillis: Long = 10_000L,
        maxDurationMillis: Long = 60 * 60_000L
    ) {
        if (isLiveTracking) return
        if (!hasForegroundLocationPermission()) {
            cb.onLiveError("Location permission missing")
            return
        }
        liveCallback = cb
        isLiveTracking = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMaxUpdates(LiveLocationBudget.maxUpdates(maxDurationMillis, intervalMillis))
            .build()

        try {
            fusedClient.requestLocationUpdates(request, liveFusedCallback, Looper.getMainLooper())
            Log.i(TAG, "Live tracking started, interval=${intervalMillis}ms")
        } catch (e: SecurityException) {
            cb.onLiveError("Permission denied: ${e.message}")
            isLiveTracking = false
            liveCallback = null
        }
    }

    fun stopLiveTracking() {
        if (!isLiveTracking) return
        fusedClient.removeLocationUpdates(liveFusedCallback)
        isLiveTracking = false
        liveCallback = null
        Log.i(TAG, "Live tracking stopped")
    }

    private fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun deliverAndStop(location: Location) {
        stopUpdates()
        callback?.onLocationReady(location)
        callback = null
    }

    private fun stopUpdates() {
        isRequesting = false
        handler.removeCallbacks(timeoutRunnable)
        try { fusedClient.removeLocationUpdates(fusedCallback) } catch (_: Exception) {}
    }

    fun stop() {
        stopUpdates()
        stopLiveTracking()
        callback = null
    }
}
