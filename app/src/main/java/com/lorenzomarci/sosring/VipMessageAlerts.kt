package com.lorenzomarci.sosring

import android.content.Context

enum class MessageApp(val storageId: String) {
    WHATSAPP("whatsapp"),
    GOOGLE_MESSAGES("google_messages"),
    TELEGRAM("telegram");

    companion object {
        fun fromStorageId(value: String?): MessageApp? = entries.firstOrNull { it.storageId == value }
    }
}

enum class MessageAlertState {
    UNPAIRED,
    PAIRING,
    PAIRED
}

object VipMessageAlerts {
    val supported: Boolean get() = VipMessageAlertsProvider.supported
    val apps: List<MessageApp> get() = VipMessageAlertsProvider.apps

    fun hasNotificationAccess(context: Context): Boolean =
        VipMessageAlertsProvider.hasNotificationAccess(context)

    fun state(context: Context, contact: VipContact, app: MessageApp): MessageAlertState =
        VipMessageAlertsProvider.state(context, contact, app)

    fun beginPairing(context: Context, contact: VipContact, app: MessageApp) =
        VipMessageAlertsProvider.beginPairing(context, contact, app)

    fun cancelPairing(context: Context) =
        VipMessageAlertsProvider.cancelPairing(context)

    fun unpair(context: Context, contact: VipContact, app: MessageApp) =
        VipMessageAlertsProvider.unpair(context, contact, app)

    fun onContactChanged(context: Context, oldNumber: String, newNumber: String) =
        VipMessageAlertsProvider.onContactChanged(context, oldNumber, newNumber)

    fun onContactRemoved(context: Context, number: String) =
        VipMessageAlertsProvider.onContactRemoved(context, number)
}
