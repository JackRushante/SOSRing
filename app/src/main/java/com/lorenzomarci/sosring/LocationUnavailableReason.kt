package com.lorenzomarci.sosring

/**
 * Why a one-shot location request could not be answered. The phone that was asked sends the
 * code back, so the requester learns the cause instead of waiting for the "no response" timeout.
 * Older app versions ignore the error message type they do not know.
 */
enum class LocationUnavailableReason(val code: String) {
    /** The system Location switch is off: Android blocks every source, GPS, Wi-Fi and cells alike. */
    LOCATION_OFF("location_off"),

    /** Location is on but no fix arrived before the timeout, and there is no last known position. */
    NO_FIX("no_fix"),

    /** SOS Ring may not use the location in the background ("Allow all the time" missing). */
    NO_BACKGROUND_PERMISSION("no_permission"),

    /** The monitoring service is not running, so the location cannot be read. */
    SERVICE_INACTIVE("service_inactive"),

    /** The owner has not turned on "Share my location" for the requester. */
    NOT_SHARED("not_shared"),

    /** Live tracking refused: the daily live-tracking time allowed for this requester is used up. */
    LIVE_LIMIT("live_limit");

    companion object {
        fun fromCode(code: String?): LocationUnavailableReason? =
            entries.firstOrNull { it.code == code }

        /** Cause of a failed fix: the switch may have been turned off while the fix was running. */
        fun afterFailedFix(locationEnabled: Boolean): LocationUnavailableReason =
            if (locationEnabled) NO_FIX else LOCATION_OFF
    }
}
