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

    private val outHandler = Handler(Looper.getMainLooper())
    private val inHandler = Handler(Looper.getMainLooper())

    private var outSessionId: String? = null
    private var outPeerNumber: String? = null
    private var outStartedAtMs: Long = 0L
    private var outFirstPointReceived = false

    private var inSessionId: String? = null
    private var inRequesterNumber: String? = null
    private var locationHelper: LocationHelper? = null

    val outgoingContactNumber: String? get() = outPeerNumber
    fun outgoingSessionId(): String? = outSessionId

    fun startOutgoing(context: Context, contact: VipContact, durationMinutes: Int): Boolean {
        if (outSessionId != null) return false
        val peer = PeerStore(context).get(contact.number) ?: return false
        val sessionId = UUID.randomUUID().toString()
        outSessionId = sessionId
        outPeerNumber = contact.number
        outStartedAtMs = System.currentTimeMillis()
        outFirstPointReceived = false
        sendTo(peer, P2pMessageFactory.liveStart(sessionId, durationMinutes, 10))
        outHandler.postDelayed({ stopOutgoing(context, sendEnd = true) }, durationMinutes * 60_000L)
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
            PeerStore(context).get(peerNumber)?.let { sendTo(it, P2pMessageFactory.liveStop(sessionId)) }
        }
        SosRingDatabase.getInstance(context).clearSession(sessionId)
        outSessionId = null
        outPeerNumber = null
        outFirstPointReceived = false
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
        NotificationManagerCompat.from(context).cancel(OUTGOING_NOTIFICATION_ID)
        Log.i(TAG, "outgoing live stopped session=$sessionId")
    }

    fun onPointReceived(context: Context, senderNumber: String, sessionId: String, lat: Double, lon: Double, accuracy: Double) {
        if (outSessionId != sessionId) {
            Log.w(TAG, "live point for inactive session, ignoring")
            return
        }
        SosRingDatabase.getInstance(context).insertPoint(senderNumber, sessionId, lat, lon, accuracy.toFloat(), System.currentTimeMillis())
        outFirstPointReceived = true
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
    }

    fun onEndReceived(context: Context, sessionId: String) {
        if (outSessionId == sessionId) stopOutgoing(context, sendEnd = false)
        if (inSessionId == sessionId) stopIncoming(context, sendEnd = false)
    }

    fun startIncoming(context: Context, requester: Peer, requesterName: String, sessionId: String, durationMinutes: Int, intervalSeconds: Int) {
        stopIncoming(context, sendEnd = false)
        inSessionId = sessionId
        inRequesterNumber = requester.number
        CallMonitorService.getInstance()?.setLocationForegroundType(true)
        val helper = LocationHelper(context)
        locationHelper = helper
        helper.startLiveTracking(object : LocationHelper.LiveCallback {
            override fun onLocationUpdate(location: Location) {
                val active = inSessionId ?: return
                sendTo(requester, P2pMessageFactory.livePoint(active, location.latitude, location.longitude, location.accuracy.toDouble()))
            }

            override fun onLiveError(message: String) {
                Log.e(TAG, "incoming live error: $message")
            }
        }, intervalMillis = intervalSeconds * 1000L, maxDurationMillis = durationMinutes * 60_000L)
        inHandler.removeCallbacksAndMessages(null)
        inHandler.postDelayed({ stopIncoming(context, sendEnd = true) }, durationMinutes * 60_000L)
        notify(context, context.getString(R.string.location_live_started, requesterName, durationMinutes), openAppIntent(context), ongoing = true, withStop = true, notificationId = LIVE_NOTIFICATION_ID)
        Log.i(TAG, "incoming live started session=$sessionId from ${requester.number}")
    }

    fun stopIncoming(context: Context, sendEnd: Boolean) {
        val sessionId = inSessionId ?: return
        val requesterNumber = inRequesterNumber
        locationHelper?.stopLiveTracking()
        locationHelper = null
        CallMonitorService.getInstance()?.setLocationForegroundType(false)
        inHandler.removeCallbacksAndMessages(null)
        if (sendEnd && requesterNumber != null) {
            PeerStore(context).get(requesterNumber)?.let { sendTo(it, P2pMessageFactory.liveStop(sessionId)) }
        }
        inSessionId = null
        inRequesterNumber = null
        NotificationManagerCompat.from(context).cancel(LIVE_NOTIFICATION_ID)
        Log.i(TAG, "incoming live stopped session=$sessionId")
    }

    private fun sendTo(peer: Peer, payload: ByteArray) {
        val envelope = P2pEnvelope.seal(payload, IdentityKeyStore.idPub(), IdentityKeyStore::sign)
        val body = WebPushCrypto.encrypt(envelope, peer.p256dh, peer.auth)
        WebPushSender.send(peer.endpoint, body)
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
