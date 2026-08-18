package com.lorenzomarci.sosring

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val messages = messageBundles(notification.extras)
        val shortcutId = notification.shortcutId
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        if (!WhatsAppNotificationPolicy.shouldInspect(
                packageName = sbn.packageName,
                isGroupSummary = isGroupSummary,
                category = notification.category,
                shortcutId = shortcutId,
                messageCount = messages.size
            )) return

        if (notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            Log.d(TAG, "Ignoring WhatsApp group conversation")
            return
        }

        val latestMessageTime = messages.maxOfOrNull { it.getLong(MESSAGE_TIME_KEY, 0L) }
            ?.takeIf { it > 0L }
            ?: notification.`when`.takeIf { it > 0L }
            ?: sbn.postTime
        val shortcutHash = WhatsAppNotificationPolicy.shortcutHash(shortcutId!!)
        val eventFingerprint = WhatsAppNotificationPolicy.eventFingerprint(
            shortcutId = shortcutId,
            latestMessageTime = latestMessageTime,
            messageCount = messages.size
        )

        val store = WhatsAppBindingStore(this)
        val prefs = PrefsManager(this)
        val pendingNumber = store.pendingNumber
        if (pendingNumber != null) {
            val contact = prefs.getContacts().firstOrNull {
                PhoneUtils.normalize(it.number) == pendingNumber
            }
            if (contact == null) {
                store.pendingNumber = null
                return
            }
            store.bind(contact.number, shortcutHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.whatsapp_pair_success, contact.name),
                Toast.LENGTH_LONG
            ).show()
            Log.i(TAG, "WhatsApp conversation paired with VIP; shortcut=${shortcutHash.take(10)}")
            return
        }

        val contact = store.contactForShortcut(shortcutHash, prefs.getContacts()) ?: return
        if (!store.isNewEvent(shortcutHash, eventFingerprint)) return

        if (!prefs.isServiceEnabled || prefs.isMuted || prefs.isInQuietPeriod()) {
            Log.i(TAG, "WhatsApp VIP alert skipped by monitoring state")
            return
        }

        Log.i(TAG, "New WhatsApp message from paired VIP; shortcut=${shortcutHash.take(10)}")
        CallMonitorService.playWhatsAppAlert(this, contact.number)
    }

    @Suppress("DEPRECATION")
    private fun messageBundles(extras: Bundle): List<Bundle> =
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { it as? Bundle }
            .orEmpty()

    companion object {
        private const val TAG = "SOSRingWhatsApp"
        private const val MESSAGE_TIME_KEY = "time"
    }
}
