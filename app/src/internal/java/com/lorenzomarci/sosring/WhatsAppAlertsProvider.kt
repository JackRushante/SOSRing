package com.lorenzomarci.sosring

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object WhatsAppAlertsProvider {
    const val supported: Boolean = true

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun state(context: Context, contact: VipContact): WhatsAppAlertState =
        WhatsAppBindingStore(context).state(contact.number)

    fun beginPairing(context: Context, contact: VipContact) {
        WhatsAppBindingStore(context).pendingNumber = contact.number
    }

    fun cancelPairing(context: Context) {
        WhatsAppBindingStore(context).pendingNumber = null
    }

    fun unpair(context: Context, contact: VipContact) {
        WhatsAppBindingStore(context).unpair(contact.number)
    }

    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) {
        WhatsAppBindingStore(context).migrateNumber(oldNumber, newNumber)
    }

    fun onContactRemoved(context: Context, number: String) {
        WhatsAppBindingStore(context).unpair(number)
    }
}
