package com.lorenzomarci.sosring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.net.URLEncoder

object P2pMessaging {

    private const val TAG = "P2pMessaging"
    private const val CHANNEL_ID = "sosring_push"
    private const val NOTIFICATION_ID = 7
    private const val MAX_ENVELOPE_BYTES = 4096

    fun requestLocation(context: Context, peer: Peer) {
        sendTo(context, peer, P2pMessageFactory.locRequest())
        Log.i(TAG, "position request sent to ${peer.number}")
    }

    fun handleIncoming(context: Context, envelope: ByteArray) {
        if (envelope.size > MAX_ENVELOPE_BYTES) {
            Log.w(TAG, "Envelope rejected (oversized: ${envelope.size} bytes)")
            return
        }
        val peerStore = PeerStore(context)
        val opened = P2pEnvelope.open(envelope) { peerStore.isTrusted(it) }
        if (opened == null) {
            Log.w(TAG, "Envelope rejected (untrusted sender or invalid signature)")
            return
        }
        val sender = peerStore.byIdPub(opened.senderIdPub)
        if (sender == null) {
            Log.w(TAG, "No paired peer for sender identity")
            return
        }
        val type = P2pMessageFactory.type(opened.payload)
        val isRequest = type == P2pMessageFactory.TYPE_LOC_REQUEST
        val verdict = P2pReplayGuard(context).check(
            senderIdPubB64 = WebPushCrypto.b64enc(opened.senderIdPub),
            ts = P2pMessageFactory.timestamp(opened.payload),
            now = System.currentTimeMillis(),
            enforceRateLimit = isRequest
        )
        if (verdict != FreshnessVerdict.ACCEPT) {
            Log.w(TAG, "Message rejected from ${sender.number} (freshness: $verdict)")
            return
        }
        when (type) {
            P2pMessageFactory.TYPE_LOC_REQUEST -> respondWithLocation(context, sender)
            P2pMessageFactory.TYPE_LOC_RESPONSE -> showLocation(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_START -> respondWithLiveStart(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_POINT -> handleLivePoint(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_STOP -> handleLiveStop(context, opened.payload)
            else -> Log.w(TAG, "Unknown P2P message type")
        }
    }

    private fun respondWithLocation(context: Context, requester: Peer) {
        val contact = PrefsManager(context).getContacts()
            .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
        if (contact == null || !contact.locationEnabled) {
            Log.w(TAG, "Location request from ${requester.number} dropped (sharing not enabled)")
            return
        }
        Log.i(TAG, "position request from ${requester.number}, getting fix")
        LocationHelper(context).requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                val payload = P2pMessageFactory.locResponse(
                    location.latitude, location.longitude, location.accuracy.toDouble()
                )
                sendTo(context, requester, payload)
                Log.i(TAG, "position response sent to ${requester.number}")
                val label = contact.name.ifBlank { requester.number }
                notify(context, context.getString(R.string.p2p_location_shared, label), null)
            }

            override fun onLocationFailed() {
                Log.w(TAG, "Location fix failed for ${requester.number}")
            }
        })
    }

    private fun respondWithLiveStart(context: Context, requester: Peer, payload: ByteArray) {
        val start = P2pMessageFactory.parseLiveStart(payload) ?: return
        val contact = PrefsManager(context).getContacts()
            .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
        if (contact == null || !contact.locationEnabled) {
            Log.w(TAG, "Live start from ${requester.number} dropped (sharing not enabled)")
            return
        }
        val name = contact.name.ifBlank { requester.number }
        P2pLiveController.startIncoming(context, requester, name, start.sessionId, start.durationMin, start.intervalSec)
    }

    private fun handleLivePoint(context: Context, sender: Peer, payload: ByteArray) {
        val point = P2pMessageFactory.parseLivePoint(payload) ?: return
        P2pLiveController.onPointReceived(context, sender.number, point.sessionId, point.lat, point.lon, point.accuracy)
    }

    private fun handleLiveStop(context: Context, payload: ByteArray) {
        val stop = P2pMessageFactory.parseLiveStop(payload) ?: return
        P2pLiveController.onEndReceived(context, stop.sessionId)
    }

    private fun showLocation(context: Context, sender: Peer, payload: ByteArray) {
        val loc = P2pMessageFactory.parseLocResponse(payload) ?: return
        val label = URLEncoder.encode(sender.number, "UTF-8")
        val geoUri = Uri.parse("geo:${loc.lat},${loc.lon}?q=${loc.lat},${loc.lon}($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pending = PendingIntent.getActivity(
            context, sender.number.hashCode(), mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (loc.accuracy > 0.0) {
            context.getString(R.string.location_received, sender.number, loc.accuracy.toInt())
        } else {
            context.getString(R.string.p2p_location_received_no_acc, sender.number)
        }
        notify(context, text, pending)
        Log.i(TAG, "position response shown from ${sender.number}")
    }

    private fun sendTo(context: Context, peer: Peer, payload: ByteArray) {
        val envelope = P2pEnvelope.seal(payload, IdentityKeyStore.idPub(), IdentityKeyStore::sign)
        val body = WebPushCrypto.encrypt(envelope, peer.p256dh, peer.auth)
        WebPushSender.send(peer.endpoint, body)
    }

    private fun notify(context: Context, text: String, pending: PendingIntent?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_location_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SOS Ring")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (pending != null) builder.setContentIntent(pending)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show notification: ${e.message}")
        }
    }
}
