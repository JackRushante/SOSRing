package com.lorenzomarci.sosring

import android.content.Context

object WhatsAppAlertsProvider {
    const val supported: Boolean = false

    fun hasNotificationAccess(context: Context): Boolean = false
    fun state(context: Context, contact: VipContact): WhatsAppAlertState = WhatsAppAlertState.UNPAIRED
    fun beginPairing(context: Context, contact: VipContact) = Unit
    fun cancelPairing(context: Context) = Unit
    fun unpair(context: Context, contact: VipContact) = Unit
    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) = Unit
    fun onContactRemoved(context: Context, number: String) = Unit
}
