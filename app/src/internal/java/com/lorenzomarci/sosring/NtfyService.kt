package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class NtfyService(private val context: Context) : PushEngine {

    private val prefs = PrefsManager(context)
    private val database = SosRingDatabase.getInstance(context)
    private lateinit var ntfyClient: NtfyClient
    private var locationHelper: LocationHelper? = null
    @Volatile private var running = false
    private var eventSource: EventSource? = null
    private var reconnectAttempt = 0
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingRequests = ConcurrentHashMap<String, Long>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lastPhoneChangeSeen = ConcurrentHashMap<String, Long>()
    private val lastLocRequestSeen = ConcurrentHashMap<String, Long>()
    private var liveSessionId: String? = null
    private var liveTargetTopic: String? = null
    override var liveContactNumber: String? = null
        private set
    private var incomingLiveSessionId: String? = null
    private var incomingLiveRequesterHash: String? = null
    private var incomingLiveRequesterNumber: String? = null
    private val liveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val incomingLiveHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Fine sessione su orologio a muro: i postDelayed (uptime) si fermano in
    // deep sleep, la deadline invece resta confrontabile in ogni momento.
    private var liveDeadlineMs = 0L
    private var lastLivePointAtMs = 0L
    private var liveStaleNotified = false
    private var incomingDeadlineMs = 0L
    private var incomingIntervalMs = LIVE_INTERVAL_MS
    private var lastFixAtMs = 0L
    private var incomingStaleNotified = false
    private var incomingLiveCallback: LocationHelper.LiveCallback? = null

    private val sseDedup = SseDedup()
    @Volatile private var lastSseEventTimeSec = 0L

    fun start() {
        val serverUrl = prefs.ntfyServerUrl
        val ownTopic = TopicScheme.own(prefs)
        if (ownTopic.isBlank()) {
            Log.w(TAG, "No own topic hash — skipping ntfy subscription")
            return
        }

        ntfyClient = NtfyClient(context, serverUrl)
        if (serverUrl.isBlank() || !CryptoHelper.isConfigured()) {
            Log.w(TAG, "Location sharing not configured — skipping ntfy subscription")
            return
        }
        locationHelper = LocationHelper(context)
        running = true
        database.clearAll()

        createLocationChannel()
        subscribeToTopic(serverUrl, ownTopic)
        Log.i(TAG, "NtfyService started, subscribed to $ownTopic")
    }

    override fun stop() {
        running = false
        stopLiveTracking()
        stopIncomingLiveTracking(sendStop = false)
        reconnectHandler.removeCallbacksAndMessages(null)
        eventSource?.cancel()
        eventSource = null
        locationHelper?.stop()
        locationHelper = null
        instance = null
        Log.i(TAG, "NtfyService stopped")
    }

    override fun resubscribe() {
        if (!running) return
        reconnectAttempt = 0
        eventSource?.cancel()
        eventSource = null
        val serverUrl = prefs.ntfyServerUrl
        val ownTopic = TopicScheme.own(prefs)
        if (ownTopic.isNotBlank()) subscribeToTopic(serverUrl, ownTopic)
    }

    private fun createLocationChannel() {
        val channel = android.app.NotificationChannel(
            LOCATION_CHANNEL_ID,
            context.getString(R.string.notif_location_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_location_channel_desc)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun subscribeToTopic(serverUrl: String, topic: String) {
        // since= recupera i messaggi pubblicati nei buchi di riconnessione
        // (senza, un live_stop perso in un gap sparisce per sempre)
        val request = NtfyRequestFactory.sse(serverUrl, topic, prefs.ntfyAuthToken, lastSseEventTimeSec)

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                reconnectAttempt = 0
                Log.i(TAG, "SSE connected to $topic")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                handleSseData(data)
            }

            override fun onClosed(eventSource: EventSource) {
                Log.i(TAG, "SSE closed")
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE failure: ${t?.message ?: "unknown"} (code=${response?.code})")
                if (running) {
                    val delay = ReconnectBackoff.delayMs(reconnectAttempt) + kotlin.random.Random.nextLong(0, 1000)
                    reconnectAttempt++
                    reconnectHandler.postDelayed({
                        if (running) subscribeToTopic(serverUrl, topic)
                    }, delay)
                }
            }
        }

        eventSource = EventSources.createFactory(NetworkClient.sseClient).newEventSource(request, listener)
    }

    private fun handleSseData(data: String) {
        heartbeat(System.currentTimeMillis())
        try {
            val event = JSONObject(data)
            val eventTimeSec = event.optLong("time", 0L)
            if (eventTimeSec > 0) lastSseEventTimeSec = eventTimeSec
            if (!sseDedup.markSeen(event.optString("id", ""))) return
            val message = if (event.has("message")) {
                JSONObject(event.getString("message"))
            } else if (event.has("type") && event.optString("type") != "open") {
                event
            } else return

            val type = message.optString("type", "")
            val fromHash = message.optString("from", "")
            val keyFp = message.optString("key_fp", "")

            Log.d(TAG, "Received: type=$type from=${fromHash.take(8)}... fp=${keyFp.take(8)}")

            when (type) {
                "discovery" -> handleDiscovery(fromHash, keyFp)
                "discovery_ack" -> handleDiscoveryAck(fromHash, keyFp)
                "loc_request" -> handleLocationRequest(fromHash)
                "loc_pending" -> handleLocationPending(fromHash)
                "loc_response" -> handleLocationResponse(message, fromHash)
                "key_rotated" -> handleKeyRotated(fromHash, keyFp)
                "live_start" -> handleLiveStart(message, fromHash)
                "live_point" -> handleLivePoint(message, fromHash)
                "live_stop" -> handleLiveStop(message, fromHash)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE data: ${e.message}")
        }
    }

    private fun handleDiscovery(fromHash: String, remoteFp: String) {
        val contacts = prefs.getContacts()
        val match = contacts.any { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (match) {
            val localFp = CryptoHelper.keyFingerprint()
            if (remoteFp.isNotBlank() && localFp != null && remoteFp != localFp) {
                Log.w(TAG, "Discovery from $fromHash with mismatching fingerprint")
                showNotification(context.getString(R.string.security_key_mismatch), null)
                return
            }
            Log.i(TAG, "Discovery from known VIP, sending ack")
            ntfyClient.sendDiscoveryAck(fromHash, TopicScheme.own(prefs), localFp)
        }
    }

    private fun handleDiscoveryAck(fromHash: String, remoteFp: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (contact != null) {
            val localFp = CryptoHelper.keyFingerprint()
            if (remoteFp.isNotBlank() && localFp != null && remoteFp != localFp) {
                Log.w(TAG, "Discovery ack from ${contact.name} with mismatching fingerprint")
                showNotification(context.getString(R.string.security_key_mismatch), null)
                return
            }
            Log.i(TAG, "Discovery ack from ${contact.name}, enabling location")
            prefs.updateContactLocationEnabled(contact.number, true)
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
        }
    }

    private fun handleKeyRotated(fromHash: String, remoteFp: String) {
        val now = System.currentTimeMillis()
        val lastSeen = lastPhoneChangeSeen[fromHash] ?: 0L
        if (now - lastSeen < PHONE_CHANGE_RATE_LIMIT_MS) {
            Log.w(TAG, "Key rotation from $fromHash rate-limited, ignoring")
            return
        }
        lastPhoneChangeSeen[fromHash] = now

        val contacts = prefs.getContacts()
        val contact = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (contact == null) {
            Log.w(TAG, "Key rotation from unknown contact, ignoring")
            return
        }

        Log.i(TAG, "Key rotation notice from ${contact.name}, fp=${remoteFp.take(8)}")
        showNotification(
            context.getString(R.string.security_key_rotated_received),
            null
        )
    }

    override fun broadcastKeyRotated() {
        val ownHash = TopicScheme.own(prefs)
        if (ownHash.isBlank()) return

        val fp = CryptoHelper.keyFingerprint()
        val contacts = prefs.getContacts()
        contacts.forEach { contact ->
            val topic = TopicScheme.forNumber(prefs,contact.number)
            ntfyClient.sendKeyRotated(topic, ownHash, fp)
        }
        Log.i(TAG, "Key rotation notice sent to ${contacts.size} contacts")
    }

    companion object {
        private const val TAG = "NtfyService"
        private const val LOCATION_CHANNEL_ID = "sosring_location"
        private const val LOCATION_NOTIFICATION_ID = 3
        private const val RESPONSE_TIMEOUT_MS = 30_000L
        private const val PHONE_CHANGE_RATE_LIMIT_MS = 60 * 60 * 1000L
        private const val LOC_REQUEST_RATE_LIMIT_MS = 30 * 1000L
        private const val LIVE_INTERVAL_SEC = 10
        private const val LIVE_INTERVAL_MS = LIVE_INTERVAL_SEC * 1000L

        private var instance: NtfyService? = null

        fun getInstance(): NtfyService? = instance

        fun canStart(context: Context): Boolean {
            val prefs = PrefsManager(context)
            return TopicScheme.own(prefs).isNotBlank() &&
                    prefs.ntfyServerUrl.isNotBlank() &&
                    CryptoHelper.isConfigured()
        }

        fun start(context: Context) {
            if (instance == null) {
                instance = NtfyService(context).also { it.start() }
            }
        }
    }

    private fun handleLocationRequest(fromHash: String) {
        val now = System.currentTimeMillis()
        val lastSeen = lastLocRequestSeen[fromHash] ?: 0L
        if (now - lastSeen < LOC_REQUEST_RATE_LIMIT_MS) {
            Log.w(TAG, "Location request from $fromHash rate-limited (${(now - lastSeen) / 1000}s ago)")
            return
        }
        lastLocRequestSeen[fromHash] = now

        val contacts = prefs.getContacts()
        val sender = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (sender == null) {
            Log.w(TAG, "Location request from unknown hash, ignoring")
            return
        }

        Log.i(TAG, "Location request from ${sender.name}, getting GPS fix...")
        prefs.addLocationLog(sender.name, sender.number, "incoming")
        ntfyClient.sendLocationPending(fromHash, TopicScheme.own(prefs))

        locationHelper?.requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Sending location fix (acc=${location.accuracy})")
                val senderNumber = sender.number
                val myNumber = prefs.ownPhoneNumber
                ntfyClient.sendLocationResponse(
                    fromHash, TopicScheme.own(prefs),
                    location.latitude, location.longitude, location.accuracy,
                    myNumber, senderNumber
                )
            }

            override fun onLocationFailed() {
                Log.w(TAG, "Failed to get location for request from ${sender.name}")
            }
        })
    }

    private fun handleLocationPending(fromHash: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        val name = contact?.name ?: "Unknown"

        showNotification(
            context.getString(R.string.location_pending, name),
            null
        )
    }

    private fun handleLocationResponse(message: JSONObject, fromHash: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        val name = contact?.name ?: "Unknown"

        val encData = message.optString("enc", "")
        if (encData.isBlank() || contact == null) {
            Log.w(TAG, "Location response without encrypted payload, ignoring")
            return
        }
        val decrypted = CryptoHelper.decrypt(encData, prefs.ownPhoneNumber, contact.number)
        if (decrypted == null) {
            Log.e(TAG, "Failed to decrypt location from $name")
            return
        }
        val locJson = JSONObject(decrypted)
        val lat = locJson.getDouble("lat")
        val lon = locJson.getDouble("lon")
        val acc = locJson.optDouble("acc", 0.0).toInt()

        pendingRequests.remove(fromHash)

        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($name)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, fromHash.hashCode(), mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        showNotification(
            context.getString(R.string.location_received, name, acc),
            pendingIntent
        )
    }

    private fun handleLiveStart(message: JSONObject, fromHash: String) {
        val contacts = prefs.getContacts()
        val requester = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (requester == null) {
            Log.w(TAG, "Live start from unknown hash, ignoring")
            return
        }
        if (locationHelper == null) {
            Log.w(TAG, "Live start unavailable: no location helper")
            return
        }

        val sessionId = message.optString("session_id", "")
        if (sessionId.isBlank()) {
            Log.w(TAG, "Live start without session id, ignoring")
            return
        }

        val durationMinutes = message.optInt("duration_min", 15).coerceIn(1, 60)
        val intervalSeconds = message.optInt("interval_sec", 10).coerceIn(5, 60)
        startIncomingLiveTracking(requester, fromHash, sessionId, durationMinutes, intervalSeconds)
    }

    private fun startIncomingLiveTracking(
        requester: VipContact,
        requesterHash: String,
        sessionId: String,
        durationMinutes: Int,
        intervalSeconds: Int
    ) {
        locationHelper?.stopLiveTracking()
        incomingLiveSessionId = sessionId
        incomingLiveRequesterHash = requesterHash
        incomingLiveRequesterNumber = requester.number
        incomingDeadlineMs = LiveSessionPolicy.deadlineMs(System.currentTimeMillis(), durationMinutes)
        incomingIntervalMs = intervalSeconds * 1000L
        lastFixAtMs = System.currentTimeMillis()
        incomingStaleNotified = false
        CallMonitorService.getInstance()?.setLocationForegroundType(true)

        val callback = object : LocationHelper.LiveCallback {
            override fun onLocationUpdate(location: Location) {
                val activeSession = incomingLiveSessionId ?: return
                val now = System.currentTimeMillis()
                if (LiveSessionPolicy.isExpired(incomingDeadlineMs, now)) {
                    stopIncomingLiveTracking(sendStop = true)
                    return
                }
                lastFixAtMs = now
                incomingStaleNotified = false
                ntfyClient.sendLivePoint(
                    requesterHash,
                    TopicScheme.own(prefs),
                    activeSession,
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    prefs.ownPhoneNumber,
                    requester.number
                )
            }

            override fun onLiveError(message: String) {
                Log.e(TAG, "Incoming live error: $message")
                showNotification("Live tracking error: $message", openAppIntent())
            }
        }
        incomingLiveCallback = callback
        locationHelper?.startLiveTracking(callback, intervalMillis = incomingIntervalMs, maxDurationMillis = durationMinutes * 60_000L)

        incomingLiveHandler.removeCallbacksAndMessages(null)
        incomingLiveHandler.postDelayed({
            stopIncomingLiveTracking(sendStop = true)
        }, durationMinutes * 60 * 1000L)
        incomingLiveHandler.postDelayed(incomingWatchdog, LiveSessionPolicy.WATCHDOG_TICK_MS)

        Log.i(TAG, "Incoming live tracking started for ${requester.name}, session=$sessionId")
        showNotification(context.getString(R.string.location_live_started, requester.name, durationMinutes), openAppIntent())
    }

    // Watchdog lato target: chiude a deadline e, se il FLP smette di consegnare
    // fix (budget esaurito, provider spento), riavvia la richiesta per il tempo
    // residuo invece di lasciare morire la sessione in silenzio.
    private val incomingWatchdog = object : Runnable {
        override fun run() {
            if (incomingLiveSessionId == null) return
            val now = System.currentTimeMillis()
            if (LiveSessionPolicy.isExpired(incomingDeadlineMs, now)) {
                stopIncomingLiveTracking(sendStop = true)
                return
            }
            if (LiveSessionPolicy.isStale(lastFixAtMs, incomingIntervalMs, now)) {
                restartIncomingLocationUpdates(now)
            }
            incomingLiveHandler.postDelayed(this, LiveSessionPolicy.WATCHDOG_TICK_MS)
        }
    }

    private fun restartIncomingLocationUpdates(nowMs: Long) {
        val callback = incomingLiveCallback ?: return
        val remainingMs = incomingDeadlineMs - nowMs
        if (remainingMs <= 0) return
        Log.w(TAG, "No GPS fix for ${(nowMs - lastFixAtMs) / 1000}s during live session, restarting updates")
        if (!incomingStaleNotified) {
            incomingStaleNotified = true
            showNotification(context.getString(R.string.live_send_stalled), openAppIntent())
        }
        locationHelper?.stopLiveTracking()
        locationHelper?.startLiveTracking(callback, intervalMillis = incomingIntervalMs, maxDurationMillis = remainingMs)
        // finestra piena prima di un eventuale prossimo restart
        lastFixAtMs = nowMs
    }

    private fun handleLivePoint(message: JSONObject, fromHash: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { TopicScheme.forNumber(prefs,it.number) == fromHash }
        if (contact == null) {
            Log.w(TAG, "Live point from unknown hash, ignoring")
            return
        }

        val encData = message.optString("enc", "")
        if (encData.isBlank()) {
            Log.w(TAG, "Live point without encrypted payload, ignoring")
            return
        }

        val decrypted = CryptoHelper.decrypt(encData, prefs.ownPhoneNumber, contact.number)
        if (decrypted == null) {
            Log.e(TAG, "Failed to decrypt live point from ${contact.name}")
            return
        }

        val locJson = JSONObject(decrypted)
        val sessionId = locJson.optString("session_id", "")
        if (sessionId.isBlank()) {
            Log.w(TAG, "Live point without session id, ignoring")
            return
        }

        database.insertPoint(
            contact.number,
            sessionId,
            locJson.getDouble("lat"),
            locJson.getDouble("lon"),
            locJson.optDouble("acc", 0.0).toFloat(),
            System.currentTimeMillis()
        )
        lastLivePointAtMs = System.currentTimeMillis()
        liveStaleNotified = false
        pendingRequests.remove(fromHash)
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
    }

    private fun handleLiveStop(message: JSONObject, fromHash: String) {
        val sessionId = message.optString("session_id", "")
        if (sessionId.isNotBlank() && sessionId == incomingLiveSessionId && fromHash == incomingLiveRequesterHash) {
            stopIncomingLiveTracking(sendStop = false)
        }
        if (sessionId.isNotBlank() && sessionId == liveSessionId && fromHash == liveTargetTopic) {
            clearOutgoingLiveState()
        }
    }

    private fun showNotification(text: String, pendingIntent: PendingIntent?) {
        val builder = NotificationCompat.Builder(context, LOCATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SOS Ring")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(LOCATION_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show notification: ${e.message}")
        }
    }

    override fun requestLocation(contact: VipContact) {
        val targetTopic = TopicScheme.forNumber(prefs,contact.number)
        val ownHash = TopicScheme.own(prefs)

        Log.i(TAG, "Requesting location from ${contact.name}")
        // Only incoming requests are logged (who asked MY position)
        ntfyClient.sendLocationRequest(targetTopic, ownHash)

        showNotification(
            context.getString(R.string.location_request_sent, contact.name),
            null
        )

        pendingRequests[targetTopic] = System.currentTimeMillis()
        handler.postDelayed({
            if (pendingRequests.containsKey(targetTopic)) {
                pendingRequests.remove(targetTopic)
                showNotification(
                    context.getString(R.string.location_no_response, contact.name),
                    null
                )
            }
        }, RESPONSE_TIMEOUT_MS)
    }

    override fun runDiscovery() {
        val ownHash = TopicScheme.own(prefs)
        if (ownHash.isBlank()) return

        val fp = CryptoHelper.keyFingerprint()
        val contacts = prefs.getContacts()
        contacts.forEach { contact ->
            val topic = TopicScheme.forNumber(prefs,contact.number)
            ntfyClient.sendDiscovery(topic, ownHash, fp)
        }
        Log.i(TAG, "Discovery sent to ${contacts.size} contacts")
    }

    override fun startLiveTracking(contact: VipContact, durationMinutes: Int): Boolean {
        if (liveSessionId != null) {
            Log.w(TAG, "Live tracking already active, stopping previous")
            stopLiveTracking()
        }
        val sessionId = java.util.UUID.randomUUID().toString()
        liveSessionId = sessionId
        liveContactNumber = contact.number

        val targetTopic = TopicScheme.forNumber(prefs,contact.number)
        liveTargetTopic = targetTopic
        val ownHash = TopicScheme.own(prefs)

        val durationMs = durationMinutes * 60 * 1000L
        liveDeadlineMs = LiveSessionPolicy.deadlineMs(System.currentTimeMillis(), durationMinutes)
        lastLivePointAtMs = 0L
        liveStaleNotified = false
        ntfyClient.sendLiveStart(targetTopic, ownHash, sessionId, durationMinutes, intervalSeconds = LIVE_INTERVAL_SEC)
        liveHandler.postDelayed({ stopLiveTracking() }, durationMs)
        liveHandler.postDelayed(outgoingWatchdog, LiveSessionPolicy.WATCHDOG_TICK_MS)
        Log.i(TAG, "Remote live tracking requested for ${contact.name}, session=$sessionId, duration=${durationMinutes}min")
        showNotification(context.getString(R.string.location_live_started, contact.name, durationMinutes), liveMapIntent(sessionId, contact.name))
        return true
    }

    // Watchdog lato viewer: chiude la sessione a deadline (anche se il timer
    // uptime è in ritardo) e avvisa se i punti smettono di arrivare.
    private val outgoingWatchdog = object : Runnable {
        override fun run() {
            val session = liveSessionId ?: return
            val now = System.currentTimeMillis()
            if (LiveSessionPolicy.isExpired(liveDeadlineMs, now)) {
                stopLiveTracking()
                return
            }
            if (!liveStaleNotified && LiveSessionPolicy.isStale(lastLivePointAtMs, LIVE_INTERVAL_MS, now)) {
                liveStaleNotified = true
                val name = prefs.getContacts().find { it.number == liveContactNumber }?.name ?: "?"
                val staleSec = (now - lastLivePointAtMs) / 1000
                showNotification(
                    context.getString(R.string.live_updates_stalled, name, staleSec),
                    liveMapIntent(session, name)
                )
            }
            liveHandler.postDelayed(this, LiveSessionPolicy.WATCHDOG_TICK_MS)
        }
    }

    override fun heartbeat(nowMs: Long) {
        if (liveSessionId != null && LiveSessionPolicy.isExpired(liveDeadlineMs, nowMs)) {
            stopLiveTracking()
        }
        if (incomingLiveSessionId != null && LiveSessionPolicy.isExpired(incomingDeadlineMs, nowMs)) {
            stopIncomingLiveTracking(sendStop = true)
        }
    }

    override fun stopLiveTracking() {
        liveHandler.removeCallbacksAndMessages(null)
        val activeSession = liveSessionId
        val targetTopic = liveTargetTopic
        if (activeSession != null && targetTopic != null) {
            ntfyClient.sendLiveStop(targetTopic, TopicScheme.own(prefs), activeSession)
        }
        val wasActive = activeSession != null
        clearOutgoingLiveState()
        if (wasActive) {
            Log.i(TAG, "Live tracking stopped")
            showNotification(context.getString(R.string.location_live_stopped), openAppIntent())
        }
    }

    private fun clearOutgoingLiveState() {
        liveSessionId?.let { database.clearSession(it) }
        liveSessionId = null
        liveTargetTopic = null
        liveContactNumber = null
        liveDeadlineMs = 0L
        lastLivePointAtMs = 0L
        liveStaleNotified = false
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(Push.ACTION_CONTACTS_UPDATED))
    }

    private fun stopIncomingLiveTracking(sendStop: Boolean) {
        val activeSession = incomingLiveSessionId
        val requesterHash = incomingLiveRequesterHash
        locationHelper?.stopLiveTracking()
        CallMonitorService.getInstance()?.setLocationForegroundType(false)
        incomingLiveHandler.removeCallbacksAndMessages(null)
        incomingLiveSessionId = null
        incomingLiveRequesterHash = null
        incomingLiveRequesterNumber = null
        incomingLiveCallback = null
        incomingDeadlineMs = 0L
        lastFixAtMs = 0L
        incomingStaleNotified = false
        if (sendStop && activeSession != null && requesterHash != null) {
            ntfyClient.sendLiveStop(requesterHash, TopicScheme.own(prefs), activeSession)
        }
        Log.i(TAG, "Incoming live tracking stopped")
    }

    fun isLiveTracking(): Boolean = liveSessionId != null
    override fun getLiveSessionId(): String? = liveSessionId

    private fun liveMapIntent(sessionId: String, contactName: String): PendingIntent {
        val intent = LiveMapActivity.intent(context, sessionId, contactName, true).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
