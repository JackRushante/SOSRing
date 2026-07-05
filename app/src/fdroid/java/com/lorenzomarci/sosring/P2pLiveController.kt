package com.lorenzomarci.sosring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.UUID

object P2pLiveController {

    private const val TAG = "P2pLiveController"
    private const val CHANNEL_ID = "sosring_push"
    private const val LIVE_NOTIFICATION_ID = 8
    private const val OUTGOING_NOTIFICATION_ID = 9
    private const val STALL_NOTIFICATION_ID = 10
    private const val LIVE_INTERVAL_SEC = 10
    private const val LIVE_INTERVAL_MS = LIVE_INTERVAL_SEC * 1000L

    private val outHandler = Handler(Looper.getMainLooper())
    private val inHandler = Handler(Looper.getMainLooper())

    private var outSessionId: String? = null
    private var outPeerNumber: String? = null
    private var outPeerName: String? = null
    private var outStartedAtMs: Long = 0L
    private var outFirstPointReceived = false

    // Deadline su orologio a muro: i postDelayed (uptime) si fermano in deep
    // sleep, la fine sessione deve restare verificabile in ogni momento.
    private var outDeadlineMs = 0L
    private var lastPointAtMs = 0L
    private var outStaleNotified = false

    private var inSessionId: String? = null
    private var inRequesterNumber: String? = null
    private var inStartedAtMs: Long = 0L
    private var inDeadlineMs = 0L
    private var inIntervalMs = LIVE_INTERVAL_MS
    private var lastFixAtMs = 0L
    private var inStaleNotified = false
    private var inCallback: LocationHelper.LiveCallback? = null
    private var locationHelper: LocationHelper? = null

    val outgoingContactNumber: String? get() = outPeerNumber
    fun outgoingSessionId(): String? = outSessionId
    fun hasActiveIncomingSession(): Boolean = inSessionId != null

    fun startOutgoing(context: Context, contact: VipContact, durationMinutes: Int): Boolean {
        if (outSessionId != null) return false
        val peer = PeerStore(context).get(contact.number) ?: return false
        val sessionId = UUID.randomUUID().toString()
        outSessionId = sessionId
        outPeerNumber = contact.number
        outPeerName = contact.name
        outStartedAtMs = System.currentTimeMillis()
        outFirstPointReceived = false
        outDeadlineMs = LiveSessionPolicy.deadlineMs(outStartedAtMs, durationMinutes)
        lastPointAtMs = 0L
        outStaleNotified = false
        sendTo(peer, P2pMessageFactory.liveStart(sessionId, durationMinutes, LIVE_INTERVAL_SEC), ControlRetryPolicy.MAX_ATTEMPTS)
        outHandler.postDelayed({ stopOutgoing(context, sendEnd = true) }, durationMinutes * 60_000L)
        scheduleOutgoingWatchdog(context.applicationContext)
        outHandler.postDelayed({
            if (outSessionId == sessionId &&
                LiveNoResponsePolicy.timedOut(outStartedAtMs, outFirstPointReceived, System.currentTimeMillis())
            ) {
                notify(context, context.getString(R.string.location_no_response, contact.name), null, ongoing = false, notificationId = OUTGOING_NOTIFICATION_ID)
            }
        }, LiveNoResponsePolicy.NO_RESPONSE_MS)
        notify(
            context,
            context.getString(R.string.location_live_started, contact.name, durationMinutes),
            liveMapIntent(context, sessionId, contact.name),
            ongoing = false,
            notificationId = OUTGOING_NOTIFICATION_ID
        )
        Log.i(TAG, "outgoing live started session=$sessionId to ${contact.number}")
        return true
    }

    fun stopOutgoing(context: Context, sendEnd: Boolean) {
        val sessionId = outSessionId ?: return
        val peerNumber = outPeerNumber
        outHandler.removeCallbacksAndMessages(null)
        if (sendEnd && peerNumber != null) {
            PeerStore(context).get(peerNumber)?.let { sendTo(it, P2pMessageFactory.liveStop(sessionId), ControlRetryPolicy.MAX_ATTEMPTS) }
        }
        SosRingDatabase.getInstance(context).clearSession(sessionId)
        outSessionId = null
        outPeerNumber = null
        outPeerName = null
        outFirstPointReceived = false
        outDeadlineMs = 0L
        lastPointAtMs = 0L
        outStaleNotified = false
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
        NotificationManagerCompat.from(context).cancel(OUTGOING_NOTIFICATION_ID)
        Log.i(TAG, "outgoing live stopped session=$sessionId")
    }

    fun onPointReceived(context: Context, senderNumber: String, sessionId: String, lat: Double, lon: Double, accuracy: Double) {
        if (outSessionId != sessionId) {
            Log.w(TAG, "live point for inactive session, ignoring")
            return
        }
        val peerNumber = outPeerNumber
        if (peerNumber == null || !PhoneUtils.matches(senderNumber, peerNumber)) {
            Log.w(TAG, "live point for session=$sessionId from unexpected sender, ignoring")
            return
        }
        SosRingDatabase.getInstance(context).insertPoint(senderNumber, sessionId, lat, lon, accuracy.toFloat(), System.currentTimeMillis())
        outFirstPointReceived = true
        lastPointAtMs = System.currentTimeMillis()
        outStaleNotified = false
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
    }

    fun onEndReceived(context: Context, senderNumber: String, sessionId: String) {
        if (outSessionId == sessionId) {
            val peerNumber = outPeerNumber
            if (peerNumber != null && PhoneUtils.matches(senderNumber, peerNumber)) {
                stopOutgoing(context, sendEnd = false)
            } else {
                Log.w(TAG, "live end for session=$sessionId from unexpected sender, ignoring")
            }
        }
        if (inSessionId == sessionId) {
            val requesterNumber = inRequesterNumber
            if (requesterNumber != null && PhoneUtils.matches(senderNumber, requesterNumber)) {
                stopIncoming(context, sendEnd = false)
            } else {
                Log.w(TAG, "live end for session=$sessionId from unexpected sender, ignoring")
            }
        }
    }

    fun startIncoming(context: Context, requester: Peer, requesterName: String, sessionId: String, durationMinutes: Int, intervalSeconds: Int) {
        if (inSessionId != null && inRequesterNumber == requester.number) {
            Log.w(TAG, "live start from ${requester.number} ignored, session already active for this peer")
            return
        }
        val requestedMs = durationMinutes * 60_000L
        val usedMsToday = LiveConsentUsageStore(context).usedMsToday(requester.number)
        if (!LiveConsentBudget.allow(usedMsToday, requestedMs, LiveConsentBudget.DAILY_CAP_MS)) {
            Log.w(TAG, "live start from ${requester.number} rejected, daily budget exceeded")
            return
        }
        stopIncoming(context, sendEnd = false)
        inSessionId = sessionId
        inRequesterNumber = requester.number
        inStartedAtMs = System.currentTimeMillis()
        inDeadlineMs = LiveSessionPolicy.deadlineMs(inStartedAtMs, durationMinutes)
        inIntervalMs = intervalSeconds * 1000L
        lastFixAtMs = inStartedAtMs
        inStaleNotified = false
        CallMonitorService.getInstance()?.setLocationForegroundType(true)
        val helper = LocationHelper(context)
        locationHelper = helper
        val appContext = context.applicationContext
        val callback = object : LocationHelper.LiveCallback {
            override fun onLocationUpdate(location: Location) {
                val active = inSessionId ?: return
                val now = System.currentTimeMillis()
                if (LiveSessionPolicy.isExpired(inDeadlineMs, now)) {
                    stopIncoming(appContext, sendEnd = true)
                    return
                }
                lastFixAtMs = now
                inStaleNotified = false
                val contact = PrefsManager(appContext).getContacts()
                    .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
                if (contact == null || !contact.locationEnabled) {
                    Log.w(TAG, "consent revoked mid-session for ${requester.number}, stopping")
                    stopIncoming(appContext, sendEnd = true)
                    return
                }
                sendTo(requester, P2pMessageFactory.livePoint(active, location.latitude, location.longitude, location.accuracy.toDouble()))
            }

            override fun onLiveError(message: String) {
                Log.e(TAG, "incoming live error: $message")
            }
        }
        inCallback = callback
        helper.startLiveTracking(callback, intervalMillis = inIntervalMs, maxDurationMillis = durationMinutes * 60_000L)
        inHandler.removeCallbacksAndMessages(null)
        inHandler.postDelayed({ stopIncoming(context, sendEnd = true) }, durationMinutes * 60_000L)
        inHandler.postDelayed({ incomingWatchdogTick(appContext) }, LiveSessionPolicy.WATCHDOG_TICK_MS)
        notify(context, context.getString(R.string.location_live_started, requesterName, durationMinutes), openAppIntent(context), ongoing = true, withStop = true, notificationId = LIVE_NOTIFICATION_ID)
        Log.i(TAG, "incoming live started session=$sessionId from ${requester.number}")
    }

    fun stopIncomingIfRequester(context: Context, number: String) {
        if (P2pRevocationPolicy.matchesActiveRequester(inRequesterNumber, number)) {
            stopIncoming(context, sendEnd = true)
        }
    }

    fun stopIncoming(context: Context, sendEnd: Boolean) {
        val sessionId = inSessionId ?: return
        val requesterNumber = inRequesterNumber
        val startedAtMs = inStartedAtMs
        locationHelper?.stopLiveTracking()
        locationHelper = null
        CallMonitorService.getInstance()?.setLocationForegroundType(false)
        inHandler.removeCallbacksAndMessages(null)
        if (sendEnd && requesterNumber != null) {
            PeerStore(context).get(requesterNumber)?.let { sendTo(it, P2pMessageFactory.liveStop(sessionId), ControlRetryPolicy.MAX_ATTEMPTS) }
        }
        if (requesterNumber != null) {
            LiveConsentUsageStore(context).addUsage(requesterNumber, System.currentTimeMillis() - startedAtMs, startedAtMs)
        }
        inSessionId = null
        inRequesterNumber = null
        inStartedAtMs = 0L
        inDeadlineMs = 0L
        lastFixAtMs = 0L
        inStaleNotified = false
        inCallback = null
        NotificationManagerCompat.from(context).cancel(LIVE_NOTIFICATION_ID)
        Log.i(TAG, "incoming live stopped session=$sessionId")
    }

    private fun sendTo(peer: Peer, payload: ByteArray, attempts: Int = 1) {
        val envelope = P2pEnvelope.seal(payload, IdentityKeyStore.idPub(), IdentityKeyStore::sign)
        val body = WebPushCrypto.encrypt(envelope, peer.p256dh, peer.auth)
        WebPushSender.send(peer.endpoint, body, attempts)
    }

    /**
     * Enforcement su orologio a muro, chiamato dai poll della UI: chiude le
     * sessioni la cui deadline è passata mentre i timer uptime dormivano.
     */
    fun heartbeat(context: Context, nowMs: Long) {
        if (outSessionId != null && LiveSessionPolicy.isExpired(outDeadlineMs, nowMs)) {
            stopOutgoing(context, sendEnd = true)
        }
        if (inSessionId != null && LiveSessionPolicy.isExpired(inDeadlineMs, nowMs)) {
            stopIncoming(context, sendEnd = true)
        }
    }

    private fun scheduleOutgoingWatchdog(appContext: Context) {
        outHandler.postDelayed({ outgoingWatchdogTick(appContext) }, LiveSessionPolicy.WATCHDOG_TICK_MS)
    }

    private fun outgoingWatchdogTick(appContext: Context) {
        if (outSessionId == null) return
        val now = System.currentTimeMillis()
        if (LiveSessionPolicy.isExpired(outDeadlineMs, now)) {
            stopOutgoing(appContext, sendEnd = true)
            return
        }
        if (!outStaleNotified && outFirstPointReceived && LiveSessionPolicy.isStale(lastPointAtMs, LIVE_INTERVAL_MS, now)) {
            outStaleNotified = true
            val staleSec = (now - lastPointAtMs) / 1000
            notify(
                appContext,
                appContext.getString(R.string.live_updates_stalled, outPeerName ?: "?", staleSec),
                null,
                ongoing = false,
                notificationId = STALL_NOTIFICATION_ID
            )
        }
        scheduleOutgoingWatchdog(appContext)
    }

    private fun incomingWatchdogTick(appContext: Context) {
        if (inSessionId == null) return
        val now = System.currentTimeMillis()
        if (LiveSessionPolicy.isExpired(inDeadlineMs, now)) {
            stopIncoming(appContext, sendEnd = true)
            return
        }
        if (LiveSessionPolicy.isStale(lastFixAtMs, inIntervalMs, now)) {
            restartIncomingUpdates(appContext, now)
        }
        inHandler.postDelayed({ incomingWatchdogTick(appContext) }, LiveSessionPolicy.WATCHDOG_TICK_MS)
    }

    // Fail-safe: il provider può smettere di consegnare fix in silenzio (budget
    // maxUpdates esaurito, provider disabilitato): riavvia la richiesta per il
    // tempo residuo invece di lasciare morire la sessione.
    private fun restartIncomingUpdates(appContext: Context, nowMs: Long) {
        val callback = inCallback ?: return
        val remainingMs = inDeadlineMs - nowMs
        if (remainingMs <= 0) return
        Log.w(TAG, "No GPS fix for ${(nowMs - lastFixAtMs) / 1000}s during live session, restarting updates")
        if (!inStaleNotified) {
            inStaleNotified = true
            notify(appContext, appContext.getString(R.string.live_send_stalled), openAppIntent(appContext), ongoing = false, notificationId = STALL_NOTIFICATION_ID)
        }
        locationHelper?.stopLiveTracking()
        locationHelper?.startLiveTracking(callback, intervalMillis = inIntervalMs, maxDurationMillis = remainingMs)
        lastFixAtMs = nowMs
    }

    private fun notify(context: Context, text: String, contentIntent: PendingIntent?, ongoing: Boolean, withStop: Boolean = false, notificationId: Int = LIVE_NOTIFICATION_ID) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_location_channel_name), NotificationManager.IMPORTANCE_HIGH)
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SOS Ring")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (withStop) {
            val stopIntent = Intent(context, P2pLiveStopReceiver::class.java).apply { action = P2pLiveStopReceiver.ACTION_STOP }
            val stopPending = PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, context.getString(R.string.location_live_stop), stopPending)
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "notify failed: ${e.message}")
        }
    }

    private fun liveMapIntent(context: Context, sessionId: String, contactName: String): PendingIntent {
        val intent = LiveMapActivity.intent(context, sessionId, contactName, true).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
