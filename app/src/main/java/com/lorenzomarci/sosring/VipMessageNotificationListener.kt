package com.lorenzomarci.sosring

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

class VipMessageNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        Log.w(TAG, "Notification listener disconnected, asking for a rebind")
        requestRebind(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn, "legacy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handleNotification(sbn, "ranking")
    }

    @Suppress("DEPRECATION")
    private fun handleNotification(sbn: StatusBarNotification, callback: String) {
        if (sbn.packageName == packageName) return
        val app = VipMessageNotificationPolicy.appForPackage(sbn.packageName, defaultSmsPackage()) ?: return
        val notification = sbn.notification ?: return
        val rawMessages = notification.extras
            .getParcelableArray(Notification.EXTRA_MESSAGES)
            .orEmpty()
        val bundles = rawMessages.mapNotNull { it as? Bundle }
        val conversationId = VipMessageNotificationPolicy.conversationId(
            app = app,
            packageName = sbn.packageName,
            shortcutId = notification.shortcutId,
            tag = sbn.tag,
            notificationId = sbn.id
        )
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val isGroupConversation = notification.extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        val diagnosticsEnabled = BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)
        if (diagnosticsEnabled) {
            Log.i(
                TAG,
                "Message callback=$callback app=${app.storageId} summary=$isGroupSummary " +
                    "category=${notification.category} shortcut=${notification.shortcutId != null} " +
                    "tag=${sbn.tag != null} messages=${rawMessages.size} bundles=${bundles.size} " +
                    "group=$isGroupConversation ongoing=$isOngoing"
            )
        }

        if (!VipMessageNotificationPolicy.shouldInspect(
                app = app,
                isGroupSummary = isGroupSummary,
                isGroupConversation = isGroupConversation,
                category = notification.category,
                conversationId = conversationId,
                messageCount = rawMessages.size,
                isOngoing = isOngoing
            )
        ) return

        val latestMessageTime = bundles.maxOfOrNull { it.getLong(MESSAGE_TIME_KEY, 0L) }
            ?.takeIf { it > 0L }
            ?: notification.`when`.takeIf { it > 0L }
            ?: sbn.postTime
        val messageCount = maxOf(rawMessages.size, notification.number)
        val conversationHash = VipMessageNotificationPolicy.conversationHash(conversationId!!)
        val eventFingerprint = VipMessageNotificationPolicy.eventFingerprint(
            conversationId = conversationId,
            latestMessageTime = latestMessageTime,
            messageCount = messageCount
        )

        val store = VipMessageBindingStore(this)
        val prefs = PrefsManager(this)
        val contacts = prefs.getContacts()
        val smsNumbers = if (app == MessageApp.SMS) {
            SmsVipResolver.candidateNumbers(this, notification, rawMessages)
        } else {
            emptySet()
        }
        if (diagnosticsEnabled && app == MessageApp.SMS) {
            Log.i(TAG, "SMS candidate phone numbers=${smsNumbers.size}")
        }

        val pending = store.pending
        if (pending?.app == app) {
            val contact = contacts.firstOrNull { PhoneUtils.normalize(it.number) == pending.number }
            if (contact == null) {
                store.cancelPairing()
                return
            }
            if (smsNumbers.isNotEmpty() && smsNumbers.none { PhoneUtils.matches(it, contact.number) }) {
                Log.i(TAG, "Ignoring SMS conversation that does not match pending VIP")
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
            // Some SMS apps reuse one notification for every sender: trust the number when shown.
            if (smsNumbers.isNotEmpty() && smsNumbers.none { PhoneUtils.matches(it, pairedContact.number) }) {
                Log.i(TAG, "Ignoring SMS notification whose sender is not the paired VIP")
                return
            }
            if (!store.isNewEvent(app, conversationHash, eventFingerprint)) return
            playAlertIfAllowed(prefs, pairedContact, app)
            return
        }

        if (app == MessageApp.SMS) {
            val autoMatched = SmsVipResolver.uniqueVip(smsNumbers, contacts) ?: return
            store.bind(autoMatched.number, app, conversationHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.message_auto_paired, autoMatched.name),
                Toast.LENGTH_LONG
            ).show()
            Log.i(TAG, "SMS conversation auto-paired with VIP")
            playAlertIfAllowed(prefs, autoMatched, app)
        }
    }

    /** The default SMS app, re-read at most once a minute: it changes only from the system settings. */
    private fun defaultSmsPackage(): String? {
        val now = SystemClock.elapsedRealtime()
        if (now - defaultSmsCheckedAt > DEFAULT_SMS_CACHE_MS || defaultSmsCheckedAt == 0L) {
            cachedDefaultSms = try {
                Telephony.Sms.getDefaultSmsPackage(this)
            } catch (_: Exception) {
                null
            }
            defaultSmsCheckedAt = now
        }
        return cachedDefaultSms
    }

    private fun playAlertIfAllowed(prefs: PrefsManager, contact: VipContact, app: MessageApp) {
        if (!AlertGatePolicy.allowsMessage(
                monitoringEnabled = prefs.isServiceEnabled,
                paused = prefs.isMuted,
                quietHours = prefs.isInQuietPeriod(),
                messageSoundEnabled = prefs.messageSoundEnabled
            )
        ) {
            Log.i(TAG, "VIP message alert skipped by monitoring state; app=${app.storageId}")
            return
        }
        Log.i(TAG, "New message from paired VIP; app=${app.storageId}")
        CallMonitorService.playMessageAlert(this, contact.number)
    }

    private fun appName(app: MessageApp): String = getString(
        when (app) {
            MessageApp.WHATSAPP -> R.string.message_app_whatsapp
            MessageApp.SMS -> R.string.message_app_google_messages
            MessageApp.TELEGRAM -> R.string.message_app_telegram
        }
    )

    companion object {
        private const val TAG = "SOSRingMessages"
        private const val MESSAGE_TIME_KEY = "time"
        private const val DEFAULT_SMS_CACHE_MS = 60_000L

        @Volatile
        private var cachedDefaultSms: String? = null

        @Volatile
        private var defaultSmsCheckedAt = 0L

        /** True while the system keeps the listener bound; false also when the process just started. */
        @Volatile
        var connected: Boolean = false
            private set

        /** Asks the system to bind the listener again (it can stay unbound after a crash or update). */
        fun requestRebind(context: Context) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, VipMessageNotificationListener::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Listener rebind failed: ${e.message}")
            }
        }
    }
}
