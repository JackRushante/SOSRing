package com.lorenzomarci.sosring

import android.content.Context

enum class WhatsAppAlertState {
    UNPAIRED,
    PAIRING,
    PAIRED
}

object WhatsAppAlerts {
    val supported: Boolean get() = WhatsAppAlertsProvider.supported

    fun hasNotificationAccess(context: Context): Boolean =
        WhatsAppAlertsProvider.hasNotificationAccess(context)

    fun state(context: Context, contact: VipContact): WhatsAppAlertState =
        WhatsAppAlertsProvider.state(context, contact)

    fun beginPairing(context: Context, contact: VipContact) =
        WhatsAppAlertsProvider.beginPairing(context, contact)

    fun cancelPairing(context: Context) =
        WhatsAppAlertsProvider.cancelPairing(context)

    fun unpair(context: Context, contact: VipContact) =
        WhatsAppAlertsProvider.unpair(context, contact)

    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) =
        WhatsAppAlertsProvider.onContactChanged(context, oldNumber, newNumber)

    fun onContactRemoved(context: Context, number: String) =
        WhatsAppAlertsProvider.onContactRemoved(context, number)
}
