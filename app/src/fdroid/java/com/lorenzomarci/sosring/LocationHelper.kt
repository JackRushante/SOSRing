package com.lorenzomarci.sosring

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

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

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var bestLocation: Location? = null
    private var isRequesting = false

    private var liveCallback: LiveCallback? = null
    private var isLiveTracking = false
    private var liveUpdateCount = 0
    private var maxLiveUpdates = Int.MAX_VALUE
    private val liveTimeoutRunnable = Runnable { stopLiveTracking() }

    private val singleFixListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleFix(location, location.provider ?: "Platform")
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    private val liveListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            liveCallback?.onLocationUpdate(location)
            liveUpdateCount++
            if (liveUpdateCount >= maxLiveUpdates) {
                stopLiveTracking()
            }
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
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

        val lastKnown = getLastKnownLocation()
        if (lastKnown != null) {
            handleFix(lastKnown, "LastKnown")
            if (!isRequesting) return
        }

        var anyProvider = false
        for (provider in singleFixProviders()) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    500L,
                    0f,
                    singleFixListener,
                    Looper.getMainLooper()
                )
                anyProvider = true
            } catch (e: SecurityException) {
                Log.e(TAG, "Platform permission error on $provider: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Provider $provider unavailable: ${e.message}")
            }
        }

        if (!anyProvider && bestLocation == null) {
            Log.w(TAG, "No location provider available")
            stopUpdates()
            callback?.onLocationFailed()
            callback = null
            return
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
        liveUpdateCount = 0
        maxLiveUpdates = LiveLocationBudget.maxUpdates(maxDurationMillis, intervalMillis)

        var anyProvider = false
        for (provider in liveProviders()) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    intervalMillis,
                    0f,
                    liveListener,
                    Looper.getMainLooper()
                )
                anyProvider = true
            } catch (e: SecurityException) {
                cb.onLiveError("Permission denied: ${e.message}")
                isLiveTracking = false
                liveCallback = null
                locationManager.removeUpdates(liveListener)
                return
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Provider $provider unavailable: ${e.message}")
            }
        }

        if (!anyProvider) {
            cb.onLiveError("No location provider available")
            isLiveTracking = false
            liveCallback = null
            return
        }

        handler.postDelayed(liveTimeoutRunnable, maxDurationMillis)
        Log.i(TAG, "Live tracking started, interval=${intervalMillis}ms")
    }

    fun stopLiveTracking() {
        if (!isLiveTracking) return
        handler.removeCallbacks(liveTimeoutRunnable)
        locationManager.removeUpdates(liveListener)
        isLiveTracking = false
        liveCallback = null
        Log.i(TAG, "Live tracking stopped")
    }

    private fun singleFixProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        return providers
    }

    private fun liveProviders(): List<String> {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return listOf(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return listOf(LocationManager.NETWORK_PROVIDER)
        }
        return emptyList()
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
        try { locationManager.removeUpdates(singleFixListener) } catch (_: Exception) {}
    }

    fun stop() {
        stopUpdates()
        stopLiveTracking()
        callback = null
    }
}
