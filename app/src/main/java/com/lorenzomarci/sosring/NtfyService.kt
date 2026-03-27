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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class NtfyService(private val context: Context) {

    private val prefs = PrefsManager(context)
    private lateinit var ntfyClient: NtfyClient
    private var locationHelper: LocationHelper? = null
    private var sseThread: Thread? = null
    @Volatile private var running = false
    private val pendingRequests = mutableMapOf<String, Long>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun start() {
        val serverUrl = prefs.ntfyServerUrl
        val ownTopic = prefs.ownTopicHash
        if (ownTopic.isBlank()) {
            Log.w(TAG, "No own topic hash — skipping ntfy subscription")
            return
        }

        ntfyClient = NtfyClient(serverUrl)
        if (BuildConfig.LOCATION_ENABLED) {
            locationHelper = LocationHelper(context)
        }
        running = true

        createLocationChannel()
        subscribeToTopic(serverUrl, ownTopic)
        Log.i(TAG, "NtfyService started, subscribed to $ownTopic")
    }

    fun stop() {
        running = false
        sseThread?.interrupt()
        sseThread = null
        locationHelper?.stop()
        locationHelper = null
        Log.i(TAG, "NtfyService stopped")
    }

    private fun createLocationChannel() {
        val channel = android.app.NotificationChannel(
            LOCATION_CHANNEL_ID,
            "Location Sharing",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Location sharing notifications"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun subscribeToTopic(serverUrl: String, topic: String) {
        sseThread = Thread {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()

            while (running) {
                try {
                    val request = Request.Builder()
                        .url("$serverUrl/$topic/sse")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "SSE connect failed: ${response.code}")
                            Thread.sleep(5000)
                            return@use
                        }

                        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                        var line: String? = null
                        while (running && reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            if (l.startsWith("data: ")) {
                                handleSseData(l.removePrefix("data: "))
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "SSE error: ${e.message}")
                    if (running) Thread.sleep(5000)
                }
            }
        }.apply {
            isDaemon = true
            name = "ntfy-sse"
            start()
        }
    }

    private fun handleSseData(data: String) {
        try {
            val event = JSONObject(data)
            val message = if (event.has("message")) {
                JSONObject(event.getString("message"))
            } else if (event.has("type") && event.optString("type") != "open") {
                event
            } else return

            val type = message.optString("type", "")
            val fromHash = message.optString("from", "")

            Log.d(TAG, "Received: type=$type from=${fromHash.take(8)}...")

            when (type) {
                "discovery" -> handleDiscovery(fromHash)
                "discovery_ack" -> handleDiscoveryAck(fromHash)
                "loc_request" -> handleLocationRequest(fromHash)
                "loc_pending" -> handleLocationPending(fromHash)
                "loc_response" -> handleLocationResponse(message, fromHash)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE data: ${e.message}")
        }
    }

    private fun handleDiscovery(fromHash: String) {
        val contacts = prefs.getContacts()
        val match = contacts.any { prefs.topicHashForNumber(it.number) == fromHash }
        if (match) {
            Log.i(TAG, "Discovery from known VIP, sending ack")
            ntfyClient.sendDiscoveryAck(fromHash, prefs.ownTopicHash)
        }
    }

    private fun handleDiscoveryAck(fromHash: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        if (contact != null) {
            Log.i(TAG, "Discovery ack from ${contact.name}, enabling location")
            prefs.updateContactLocationEnabled(contact.number, true)
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent(ACTION_CONTACTS_UPDATED))
        }
    }

    companion object {
        private const val TAG = "NtfyService"
        private const val LOCATION_CHANNEL_ID = "sosring_location"
        private const val LOCATION_NOTIFICATION_ID = 3
        private const val RESPONSE_TIMEOUT_MS = 30_000L
        const val ACTION_CONTACTS_UPDATED = "com.lorenzomarci.sosring.CONTACTS_UPDATED"
    }

    private fun handleLocationRequest(fromHash: String) {
        if (!BuildConfig.LOCATION_ENABLED) return

        val contacts = prefs.getContacts()
        val sender = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        if (sender == null) {
            Log.w(TAG, "Location request from unknown hash, ignoring")
            return
        }

        Log.i(TAG, "Location request from ${sender.name}, getting GPS fix...")
        prefs.addLocationLog(sender.name, sender.number, "incoming")
        ntfyClient.sendLocationPending(fromHash, prefs.ownTopicHash)

        locationHelper?.requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                Log.i(TAG, "Sending location: ${location.latitude},${location.longitude} acc=${location.accuracy}")
                val senderNumber = sender.number
                val myNumber = prefs.ownPhoneNumber
                ntfyClient.sendLocationResponse(
                    fromHash, prefs.ownTopicHash,
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
        val contact = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        val name = contact?.name ?: "Unknown"

        showNotification(
            context.getString(R.string.location_pending, name),
            null
        )
    }

    private fun handleLocationResponse(message: JSONObject, fromHash: String) {
        val contacts = prefs.getContacts()
        val contact = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        val name = contact?.name ?: "Unknown"

        val encData = message.optString("enc", "")
        val lat: Double
        val lon: Double
        val acc: Int

        if (encData.isNotBlank() && contact != null) {
            val decrypted = CryptoHelper.decrypt(encData, prefs.ownPhoneNumber, contact.number)
            if (decrypted == null) {
                Log.e(TAG, "Failed to decrypt location from $name")
                return
            }
            val locJson = JSONObject(decrypted)
            lat = locJson.getDouble("lat")
            lon = locJson.getDouble("lon")
            acc = locJson.optDouble("acc", 0.0).toInt()
        } else {
            // Fallback for unencrypted messages (backwards compat during rollout)
            lat = message.getDouble("lat")
            lon = message.getDouble("lon")
            acc = message.optDouble("acc", 0.0).toInt()
        }

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

    fun requestLocation(contact: VipContact) {
        val targetTopic = prefs.topicHashForNumber(contact.number)
        val ownHash = prefs.ownTopicHash

        Log.i(TAG, "Requesting location from ${contact.name}")
        prefs.addLocationLog(contact.name, contact.number, "outgoing")
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

    fun runDiscovery() {
        val ownHash = prefs.ownTopicHash
        if (ownHash.isBlank()) return

        val contacts = prefs.getContacts()
        contacts.forEach { contact ->
            val topic = prefs.topicHashForNumber(contact.number)
            ntfyClient.sendDiscovery(topic, ownHash)
        }
        Log.i(TAG, "Discovery sent to ${contacts.size} contacts")
    }
}
