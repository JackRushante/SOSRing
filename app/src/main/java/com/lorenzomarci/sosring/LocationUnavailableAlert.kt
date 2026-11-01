package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

/** Texts and fix-it shortcuts for a location request that could not be answered. */
object LocationUnavailableAlert {

    private const val REQUEST_CODE_BASE = 7100

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Shown to whoever asked for the position. */
    fun requesterText(context: Context, reason: LocationUnavailableReason, name: String): String {
        val res = when (reason) {
            LocationUnavailableReason.LOCATION_OFF -> R.string.location_unavailable_off
            LocationUnavailableReason.NO_FIX -> R.string.location_unavailable_no_fix
            LocationUnavailableReason.NO_BACKGROUND_PERMISSION -> R.string.location_unavailable_permission
            LocationUnavailableReason.SERVICE_INACTIVE -> R.string.location_unavailable_service
        }
        return context.getString(res, name)
    }

    /** Shown on the phone that was asked, only when its owner can fix the cause. */
    fun ownerText(context: Context, reason: LocationUnavailableReason, requesterName: String): String? =
        when (reason) {
            LocationUnavailableReason.LOCATION_OFF ->
                context.getString(R.string.location_request_blocked_off, requesterName)
            LocationUnavailableReason.NO_BACKGROUND_PERMISSION ->
                context.getString(R.string.location_request_blocked_permission, requesterName)
            LocationUnavailableReason.NO_FIX, LocationUnavailableReason.SERVICE_INACTIVE -> null
        }

    /** Opens the system screen that fixes the cause: Location settings or the app's permissions. */
    fun fixIntent(context: Context, reason: LocationUnavailableReason): PendingIntent? {
        val intent = when (reason) {
            LocationUnavailableReason.LOCATION_OFF -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            LocationUnavailableReason.NO_BACKGROUND_PERMISSION -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            LocationUnavailableReason.NO_FIX, LocationUnavailableReason.SERVICE_INACTIVE -> return null
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, REQUEST_CODE_BASE + reason.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
