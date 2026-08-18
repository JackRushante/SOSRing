package com.lorenzomarci.sosring

import android.content.Context

object VipMessageAlertsProvider {
    const val supported: Boolean = false
    val apps: List<MessageApp> = emptyList()

    fun hasNotificationAccess(context: Context): Boolean = false
    fun state(context: Context, contact: VipContact, app: MessageApp): MessageAlertState = MessageAlertState.UNPAIRED
    fun beginPairing(context: Context, contact: VipContact, app: MessageApp) = Unit
    fun cancelPairing(context: Context) = Unit
    fun unpair(context: Context, contact: VipContact, app: MessageApp) = Unit
    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) = Unit
    fun onContactRemoved(context: Context, number: String) = Unit
}
