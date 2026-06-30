package com.lorenzomarci.sosring

import android.app.Activity
import android.content.Context

object PushProvider {

    const val supportsLiveTracking: Boolean = true
    const val supportsServerConfig: Boolean = true

    fun canStart(context: Context): Boolean = NtfyService.canStart(context)

    fun start(context: Context) = NtfyService.start(context)

    fun engine(): PushEngine? = NtfyService.getInstance()

    fun verifySetup(context: Context): PushSetupStatus {
        val prefs = PrefsManager(context)
        val status = NtfySetupVerifier.verify(
            serverUrl = prefs.ntfyServerUrl,
            topic = TopicScheme.own(prefs),
            token = prefs.ntfyAuthToken
        )
        return PushSetupStatus.valueOf(status.name)
    }

    fun locationBlock(context: Context, contact: VipContact): String? {
        val prefs = PrefsManager(context)
        val reason = LocationShareReadiness.check(
            ownPhoneNumber = prefs.ownPhoneNumber,
            serverUrl = prefs.ntfyServerUrl,
            encryptionConfigured = CryptoHelper.isConfigured()
        )
        return when (reason) {
            LocationShareBlockReason.NONE -> null
            LocationShareBlockReason.MISSING_OWN_NUMBER -> context.getString(R.string.location_no_number)
            LocationShareBlockReason.MISSING_SERVER -> context.getString(R.string.location_server_missing)
            LocationShareBlockReason.MISSING_KEY -> context.getString(R.string.location_key_missing)
        }
    }

    fun ensureRegistered(activity: Activity) {}

    fun requestLocation(context: Context, contact: VipContact): Boolean {
        val engine = NtfyService.getInstance() ?: return false
        engine.requestLocation(contact)
        return true
    }
}
