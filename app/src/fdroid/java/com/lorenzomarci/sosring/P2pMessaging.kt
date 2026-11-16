package com.lorenzomarci.sosring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder

object P2pMessaging {

    private const val TAG = "P2pMessaging"
    private const val CHANNEL_ID = "sosring_push"
    private const val NOTIFICATION_ID = 7
    private const val PROBLEM_NOTIFICATION_ID = 11
    private const val MAX_ENVELOPE_BYTES = 4096
    private const val LOG_TYPE_INCOMING = "incoming"
    private const val LOG_TYPE_INCOMING_DENIED = "incoming_denied"
    const val LOG_TYPE_INCOMING_FAILED = "incoming_failed"
    private const val NOTICE_PREFS = "sosring_p2p_notices"
    private const val NOTICE_INTERVAL_MS = 60L * 60L * 1000L

    fun requestLocation(context: Context, peer: Peer, label: String) {
        val appContext = context.applicationContext
        P2pPendingRequests.track(peer.number) {
            notify(appContext, appContext.getString(R.string.p2p_no_answer, label), null)
            Log.w(TAG, "no answer to position request from ${peer.number}")
        }
        sendTo(
            peer,
            P2pMessageFactory.locRequest(),
            attempts = ControlRetryPolicy.MAX_ATTEMPTS,
            ttlSeconds = WebPushSender.CONTROL_TTL_SECONDS,
            onFailure = { failure ->
                P2pPendingRequests.forget(peer.number)
                notify(appContext, deliveryFailureText(appContext, failure, label), null)
            }
        )
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
            notice(context, "untrusted", R.string.p2p_notice_untrusted_sender)
            return
        }
        val sender = peerStore.byIdPub(opened.senderIdPub)
        if (sender == null) {
            Log.w(TAG, "No single paired peer for sender identity")
            notice(context, "duplicate", R.string.p2p_notice_duplicate_identity)
            return
        }
        val type = P2pMessageFactory.type(opened.payload)
        val enforceRateLimit = (type == P2pMessageFactory.TYPE_LOC_REQUEST || type == P2pMessageFactory.TYPE_LIVE_START)
        val verdict = P2pReplayGuard(context).check(
            senderIdPubB64 = WebPushCrypto.b64enc(opened.senderIdPub),
            ts = P2pMessageFactory.timestamp(opened.payload),
            now = System.currentTimeMillis(),
            enforceRateLimit = enforceRateLimit
        )
        if (verdict != FreshnessVerdict.ACCEPT) {
            Log.w(TAG, "Message rejected from ${sender.number} (freshness: $verdict)")
            if (verdict == FreshnessVerdict.TOO_OLD || verdict == FreshnessVerdict.TOO_FUTURE) {
                notice(context, "clock", R.string.p2p_notice_clock)
            }
            return
        }
        when (type) {
            P2pMessageFactory.TYPE_LOC_REQUEST -> respondWithLocation(context, sender)
            P2pMessageFactory.TYPE_LOC_RESPONSE -> showLocation(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LOC_ERROR -> showLocationError(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_START -> respondWithLiveStart(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_POINT -> handleLivePoint(context, sender, opened.payload)
            P2pMessageFactory.TYPE_LIVE_STOP -> handleLiveStop(context, sender, opened.payload)
            P2pMessageFactory.TYPE_ENDPOINT_UPDATE -> handleEndpointUpdate(context, opened.senderIdPub, sender, opened.payload)
            else -> Log.w(TAG, "Unknown P2P message type")
        }
    }

    /** This phone's push address changed: tell every paired peer, signed like any other message. */
    fun broadcastEndpoint(context: Context, endpoint: String, p256dh: String, auth: String) {
        val peers = PeerStore(context).all()
        if (peers.isEmpty()) return
        Thread {
            val payload = P2pMessageFactory.endpointUpdate(endpoint, p256dh, auth)
            peers.distinctBy { it.idPub to it.endpoint }.forEach { peer ->
                try {
                    sendTo(
                        peer,
                        payload,
                        attempts = ControlRetryPolicy.MAX_ATTEMPTS,
                        ttlSeconds = WebPushSender.CONTROL_TTL_SECONDS
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot send endpoint update to ${peer.number}: ${e.message}")
                }
            }
            Log.i(TAG, "endpoint update sent to ${peers.size} peer(s)")
        }.start()
    }

    private fun handleEndpointUpdate(context: Context, senderIdPub: ByteArray, sender: Peer, payload: ByteArray) {
        val update = P2pMessageFactory.parseEndpointUpdate(payload) ?: return
        val changed = PeerStore(context).updateEndpoint(
            WebPushCrypto.b64enc(senderIdPub), update.endpoint, update.p256dh, update.auth
        )
        Log.i(TAG, "endpoint update from ${sender.number}: $changed pairing(s) updated")
    }

    private fun respondWithLocation(context: Context, requester: Peer) {
        val prefs = PrefsManager(context)
        val contact = prefs.getContacts()
            .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
        val allowed = contact != null && contact.locationEnabled
        val label = contact?.name?.ifBlank { requester.number } ?: requester.number
        prefs.addLocationLog(label, requester.number, if (allowed) LOG_TYPE_INCOMING else LOG_TYPE_INCOMING_DENIED)
        if (!allowed) {
            Log.w(TAG, "Location request from ${requester.number} refused (sharing not enabled)")
            replyUnavailable(context, requester, label, LocationUnavailableReason.NOT_SHARED, logFailure = false)
            return
        }
        Log.i(TAG, "position request from ${requester.number}, getting fix")
        if (!LocationUnavailableAlert.isLocationEnabled(context)) {
            Log.w(TAG, "Location request dropped: system location is off")
            replyUnavailable(context, requester, label, LocationUnavailableReason.LOCATION_OFF)
            return
        }
        if (!hasForegroundLocationPermission(context)) {
            Log.w(TAG, "Location request dropped: location permission missing")
            replyUnavailable(context, requester, label, LocationUnavailableReason.NO_BACKGROUND_PERMISSION)
            return
        }
        val locationServicePromoted = CallMonitorService.getInstance()?.also {
            it.setLocationForegroundType(true)
        } != null
        LocationHelper(context).requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                val payload = P2pMessageFactory.locResponse(
                    location.latitude, location.longitude, location.accuracy.toDouble()
                )
                sendTo(
                    requester,
                    payload,
                    attempts = ControlRetryPolicy.MAX_ATTEMPTS,
                    ttlSeconds = WebPushSender.CONTROL_TTL_SECONDS,
                    onSuccess = {
                        Log.i(TAG, "position response sent to ${requester.number}")
                        notify(context, context.getString(R.string.p2p_location_shared, label), null)
                    },
                    onFailure = { failure ->
                        Log.w(TAG, "position response to ${requester.number} not delivered: $failure")
                        PrefsManager(context).addLocationLog(label, requester.number, LOG_TYPE_INCOMING_FAILED)
                        notify(context, answerFailureText(context, failure, label), null)
                    }
                )
                demoteLocationForeground(locationServicePromoted)
            }

            override fun onLocationFailed() {
                val reason = failedFixReason(context, locationServicePromoted)
                Log.w(TAG, "Location fix failed for ${requester.number}: ${reason.code}")
                replyUnavailable(context, requester, label, reason)
                demoteLocationForeground(locationServicePromoted)
            }
        })
    }

    /**
     * Without "Allow all the time" Android hands no location to an app in the background, and
     * without the monitoring service a background fix is throttled: name that, not "no signal".
     */
    internal fun failedFixReason(context: Context, servicePromoted: Boolean): LocationUnavailableReason {
        if (!LocationUnavailableAlert.isLocationEnabled(context)) return LocationUnavailableReason.LOCATION_OFF
        if (!hasForegroundLocationPermission(context) || !hasBackgroundLocationPermission(context)) {
            return LocationUnavailableReason.NO_BACKGROUND_PERMISSION
        }
        if (!servicePromoted) return LocationUnavailableReason.SERVICE_INACTIVE
        return LocationUnavailableReason.NO_FIX
    }

    internal fun hasForegroundLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Tells the requester why no position will come, and the owner how to fix it when possible. */
    internal fun replyUnavailable(
        context: Context,
        requester: Peer,
        label: String,
        reason: LocationUnavailableReason,
        sessionId: String? = null,
        logFailure: Boolean = true
    ) {
        if (logFailure) PrefsManager(context).addLocationLog(label, requester.number, LOG_TYPE_INCOMING_FAILED)
        try {
            sendTo(
                requester,
                P2pMessageFactory.locError(reason.code, sessionId),
                attempts = ControlRetryPolicy.MAX_ATTEMPTS,
                ttlSeconds = WebPushSender.CONTROL_TTL_SECONDS,
                onFailure = { failure -> Log.w(TAG, "location error to ${requester.number} not delivered: $failure") }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cannot send location error: ${e.message}")
        }
        LocationUnavailableAlert.ownerText(context, reason, label)?.let { text ->
            notify(context, text, LocationUnavailableAlert.fixIntent(context, reason))
        }
    }

    private fun showLocationError(context: Context, sender: Peer, payload: ByteArray) {
        val code = P2pMessageFactory.parseLocError(payload) ?: return
        val reason = LocationUnavailableReason.fromCode(code) ?: LocationUnavailableReason.NO_FIX
        val label = labelFor(context, sender)
        val text = LocationUnavailableAlert.requesterText(context, reason, label)
        val sessionId = P2pMessageFactory.parseLocErrorSession(payload)
        if (sessionId != null) {
            P2pLiveController.onErrorReceived(context, sender.number, sessionId, text)
        } else {
            P2pPendingRequests.resolve(sender.number)
        }
        notify(context, text, null)
        Log.w(TAG, "position unavailable from ${sender.number}: ${reason.code}")
    }

    private fun demoteLocationForeground(wasPromoted: Boolean) {
        if (!wasPromoted) return
        if (P2pLiveController.hasActiveIncomingSession()) return
        CallMonitorService.getInstance()?.setLocationForegroundType(false)
    }

    private fun respondWithLiveStart(context: Context, requester: Peer, payload: ByteArray) {
        val start = P2pMessageFactory.parseLiveStart(payload) ?: return
        val prefs = PrefsManager(context)
        val contact = prefs.getContacts()
            .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
        val allowed = contact != null && contact.locationEnabled
        val label = contact?.name?.ifBlank { requester.number } ?: requester.number
        prefs.addLocationLog(label, requester.number, if (allowed) LOG_TYPE_INCOMING else LOG_TYPE_INCOMING_DENIED)
        if (!allowed) {
            Log.w(TAG, "Live start from ${requester.number} refused (sharing not enabled)")
            replyUnavailable(context, requester, label, LocationUnavailableReason.NOT_SHARED, start.sessionId, logFailure = false)
            return
        }
        val refusal = when {
            !LocationUnavailableAlert.isLocationEnabled(context) -> LocationUnavailableReason.LOCATION_OFF
            !hasForegroundLocationPermission(context) -> LocationUnavailableReason.NO_BACKGROUND_PERMISSION
            else -> P2pLiveController.startIncoming(
                context, requester, label, start.sessionId, start.durationMin, start.intervalSec
            )
        }
        if (refusal != null) {
            Log.w(TAG, "Live start from ${requester.number} not started: ${refusal.code}")
            replyUnavailable(context, requester, label, refusal, start.sessionId)
        }
    }

    private fun handleLivePoint(context: Context, sender: Peer, payload: ByteArray) {
        val point = P2pMessageFactory.parseLivePoint(payload) ?: return
        P2pLiveController.onPointReceived(context, sender.number, point.sessionId, point.lat, point.lon, point.accuracy)
    }

    private fun handleLiveStop(context: Context, sender: Peer, payload: ByteArray) {
        val stop = P2pMessageFactory.parseLiveStop(payload) ?: return
        P2pLiveController.onEndReceived(context, sender.number, stop.sessionId)
    }

    private fun showLocation(context: Context, sender: Peer, payload: ByteArray) {
        val loc = P2pMessageFactory.parseLocResponse(payload) ?: return
        P2pPendingRequests.resolve(sender.number)
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

    private fun labelFor(context: Context, peer: Peer): String =
        PrefsManager(context).getContacts()
            .firstOrNull { PhoneUtils.matches(peer.number, it.number) }
            ?.name?.ifBlank { null } ?: peer.number

    /** What the requester reads when the push server of [label] refused or could not be reached. */
    fun deliveryFailureText(context: Context, failure: PushFailure, label: String): String = context.getString(
        when (failure) {
            PushFailure.STALE_PAIRING -> R.string.p2p_send_failed_stale
            PushFailure.NETWORK -> R.string.p2p_send_failed_network
            PushFailure.RATE_LIMITED -> R.string.p2p_send_failed_rate_limited
            PushFailure.TOO_LARGE, PushFailure.SERVER_ERROR -> R.string.p2p_send_failed_server
        },
        label
    )

    /** What the asked phone's owner reads when the answer could not be delivered to [label]. */
    fun answerFailureText(context: Context, failure: PushFailure, label: String): String = context.getString(
        if (failure == PushFailure.STALE_PAIRING) R.string.p2p_answer_failed_stale else R.string.p2p_answer_failed,
        label
    )

    internal fun sendTo(
        peer: Peer,
        payload: ByteArray,
        attempts: Int = 1,
        ttlSeconds: Int = WebPushSender.DEFAULT_TTL_SECONDS,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((PushFailure) -> Unit)? = null
    ) {
        val envelope = P2pEnvelope.seal(payload, IdentityKeyStore.idPub(), IdentityKeyStore::sign)
        val body = WebPushCrypto.encrypt(envelope, peer.p256dh, peer.auth)
        WebPushSender.send(peer.endpoint, body, attempts, ttlSeconds, onSuccess, onFailure)
    }

    /**
     * A problem the owner of this phone must solve (a stale pairing on the other side, a wrong
     * clock): shown at most once an hour per kind, since its cause repeats with every message.
     */
    fun notice(context: Context, kind: String, textRes: Int) {
        val prefs = context.getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(kind, 0L)
        if (now - last in 0 until NOTICE_INTERVAL_MS) return
        prefs.edit().putLong(kind, now).apply()
        notify(context, context.getString(textRes), null, PROBLEM_NOTIFICATION_ID)
    }

    fun notify(context: Context, text: String, pending: PendingIntent?, id: Int = NOTIFICATION_ID) {
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (pending != null) builder.setContentIntent(pending)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show notification: ${e.message}")
        }
    }
}
