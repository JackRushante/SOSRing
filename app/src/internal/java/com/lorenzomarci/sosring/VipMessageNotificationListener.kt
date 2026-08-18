package com.lorenzomarci.sosring

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

class VipMessageNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn, "legacy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handleNotification(sbn, "ranking")
    }

    @Suppress("DEPRECATION")
    private fun handleNotification(sbn: StatusBarNotification, callback: String) {
        val app = VipMessageNotificationPolicy.appForPackage(sbn.packageName) ?: return
        val notification = sbn.notification ?: return
        val rawMessages = notification.extras
            .getParcelableArray(Notification.EXTRA_MESSAGES)
            .orEmpty()
        val bundles = rawMessages.mapNotNull { it as? Bundle }
        val conversationId = notification.shortcutId?.takeIf { it.isNotBlank() }
            ?: sbn.tag?.takeIf { it.isNotBlank() }
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isGroupConversation = notification.extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "Message callback=$callback app=${app.storageId} summary=$isGroupSummary " +
                    "category=${notification.category} shortcut=${notification.shortcutId != null} " +
                    "tag=${sbn.tag != null} messages=${rawMessages.size} bundles=${bundles.size} " +
                    "group=$isGroupConversation"
            )
        }

        if (!VipMessageNotificationPolicy.shouldInspect(
                app = app,
                isGroupSummary = isGroupSummary,
                isGroupConversation = isGroupConversation,
                category = notification.category,
                conversationId = conversationId,
                messageCount = rawMessages.size
            )
        ) return

        val latestMessageTime = bundles.maxOfOrNull { it.getLong(MESSAGE_TIME_KEY, 0L) }
            ?.takeIf { it > 0L }
            ?: notification.`when`.takeIf { it > 0L }
            ?: sbn.postTime
        val conversationHash = VipMessageNotificationPolicy.conversationHash(conversationId!!)
        val eventFingerprint = VipMessageNotificationPolicy.eventFingerprint(
            conversationId = conversationId,
            latestMessageTime = latestMessageTime,
            messageCount = rawMessages.size
        )

        val store = VipMessageBindingStore(this)
        val prefs = PrefsManager(this)
        val contacts = prefs.getContacts()
        val googleNumbers = if (app == MessageApp.GOOGLE_MESSAGES) {
            GoogleMessagesVipResolver.candidateNumbers(this, notification, rawMessages)
        } else {
            emptySet()
        }

        val pending = store.pending
        if (pending?.app == app) {
            val contact = contacts.firstOrNull { PhoneUtils.normalize(it.number) == pending.number }
            if (contact == null) {
                store.cancelPairing()
                return
            }
            if (googleNumbers.isNotEmpty() && googleNumbers.none { PhoneUtils.matches(it, contact.number) }) {
                Log.i(TAG, "Ignoring Google Messages conversation that does not match pending VIP")
                return
            }
            store.bind(contact.number, app, conversationHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.message_pair_success, appName(app), contact.name),
                Toast.LENGTH_LONG
            ).show()
            Log.i(TAG, "Message conversation paired with VIP; app=${app.storageId}")
            return
        }

        val pairedContact = store.contactForConversation(app, conversationHash, contacts)
        if (pairedContact != null) {
            if (!store.isNewEvent(app, conversationHash, eventFingerprint)) return
            playAlertIfAllowed(prefs, pairedContact, app)
            return
        }

        if (app == MessageApp.GOOGLE_MESSAGES) {
            val autoMatched = GoogleMessagesVipResolver.uniqueVip(googleNumbers, contacts) ?: return
            store.bind(autoMatched.number, app, conversationHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.message_auto_paired, autoMatched.name),
                Toast.LENGTH_LONG
            ).show()
            Log.i(TAG, "Google Messages conversation auto-paired with VIP")
            playAlertIfAllowed(prefs, autoMatched, app)
        }
    }

    private fun playAlertIfAllowed(prefs: PrefsManager, contact: VipContact, app: MessageApp) {
        if (!prefs.isServiceEnabled || prefs.isMuted || prefs.isInQuietPeriod()) {
            Log.i(TAG, "VIP message alert skipped by monitoring state; app=${app.storageId}")
            return
        }
        Log.i(TAG, "New message from paired VIP; app=${app.storageId}")
        CallMonitorService.playMessageAlert(this, contact.number)
    }

    private fun appName(app: MessageApp): String = getString(
        when (app) {
            MessageApp.WHATSAPP -> R.string.message_app_whatsapp
            MessageApp.GOOGLE_MESSAGES -> R.string.message_app_google_messages
            MessageApp.TELEGRAM -> R.string.message_app_telegram
        }
    )

    companion object {
        private const val TAG = "SOSRingMessages"
        private const val MESSAGE_TIME_KEY = "time"
    }
}
