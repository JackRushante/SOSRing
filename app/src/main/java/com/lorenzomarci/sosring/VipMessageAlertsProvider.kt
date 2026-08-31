package com.lorenzomarci.sosring

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object VipMessageAlertsProvider {
    const val supported: Boolean = true
    val apps: List<MessageApp> = MessageApp.entries

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun state(context: Context, contact: VipContact, app: MessageApp): MessageAlertState =
        VipMessageBindingStore(context).state(contact.number, app)

    fun beginPairing(context: Context, contact: VipContact, app: MessageApp) {
        VipMessageBindingStore(context).beginPairing(contact.number, app)
    }

    fun cancelPairing(context: Context) {
        VipMessageBindingStore(context).cancelPairing()
    }

    fun unpair(context: Context, contact: VipContact, app: MessageApp) {
        VipMessageBindingStore(context).unpair(contact.number, app)
    }

    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) {
        VipMessageBindingStore(context).migrateNumber(oldNumber, newNumber)
    }

    fun onContactRemoved(context: Context, number: String) {
        VipMessageBindingStore(context).unpairAll(number)
    }
}
