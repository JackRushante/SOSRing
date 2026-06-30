package com.lorenzomarci.sosring

import android.app.Activity
import android.content.Context

object Push {
    const val ACTION_CONTACTS_UPDATED = "com.lorenzomarci.sosring.CONTACTS_UPDATED"

    val supportsLiveTracking: Boolean get() = PushProvider.supportsLiveTracking
    val supportsServerConfig: Boolean get() = PushProvider.supportsServerConfig

    fun canStart(context: Context): Boolean = PushProvider.canStart(context)
    fun start(context: Context) = PushProvider.start(context)
    fun engine(): PushEngine? = PushProvider.engine()
    fun verifySetup(context: Context): PushSetupStatus = PushProvider.verifySetup(context)
    fun locationBlock(context: Context, contact: VipContact): String? = PushProvider.locationBlock(context, contact)
    fun ensureRegistered(activity: Activity) = PushProvider.ensureRegistered(activity)

    fun requestLocation(context: Context, contact: VipContact): Boolean =
        PushProvider.requestLocation(context, contact)
}
