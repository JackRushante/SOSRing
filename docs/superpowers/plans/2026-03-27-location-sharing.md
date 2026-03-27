# Location Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add peer-to-peer location sharing via ntfy push notifications, with automatic discovery and GPS response.

**Architecture:** NtfyService subscribes to the user's own topic via SSE. When a location request arrives, LocationHelper gets a high-accuracy GPS fix and NtfyClient sends the response back. Discovery handshake determines which VIP contacts have the app. Build flavors separate internal (with location) from fdroid (without).

**Tech Stack:** Kotlin, ntfy (SSE + HTTP POST), Google Play Services Location (FusedLocationProviderClient), OkHttp for HTTP, build flavors (fdroid/internal).

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/build.gradle.kts` | Modify | + build flavors, + play-services-location (internal only), + OkHttp, + buildConfigField |
| `app/src/main/res/values/strings.xml` | Modify | + location sharing English strings |
| `app/src/main/res/values-it/strings.xml` | Modify | + location sharing Italian strings |
| `app/src/main/java/.../PrefsManager.kt` | Modify | + own number/hash, + ntfy URL, + locationEnabled on VipContact |
| `app/src/main/java/.../NtfyClient.kt` | Create | HTTP POST to ntfy server (send messages) |
| `app/src/main/java/.../LocationHelper.kt` | Create | GPS fix with accuracy threshold and timeout |
| `app/src/main/java/.../NtfyService.kt` | Create | SSE subscription, message dispatch, auto-response |
| `app/src/main/res/layout/item_vip_number.xml` | Modify | + GPS icon button |
| `app/src/main/java/.../VipNumbersAdapter.kt` | Modify | + GPS icon visibility + click callback |
| `app/src/main/res/layout/activity_main.xml` | Modify | + Location Sharing settings card |
| `app/src/main/java/.../MainActivity.kt` | Modify | + settings UI, + discovery, + GPS tap handler |
| `app/src/main/java/.../CallMonitorService.kt` | Modify | + start/stop NtfyService |
| `app/src/main/AndroidManifest.xml` | Modify | + INTERNET, + location permissions, + foregroundServiceType location |

All Java/Kotlin paths are under `app/src/main/java/com/lorenzomarci/sosring/`.

---

### Task 1: Build flavors and dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add build flavors and dependencies**

Replace the entire content of `app/build.gradle.kts` with:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lorenzomarci.sosring"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lorenzomarci.sosring"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../sosring-release.jks")
            storePassword = "***REDACTED***"
            keyAlias = "sosring"
            keyPassword = "***REDACTED***"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "LOCATION_ENABLED", "false")
        }
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            buildConfigField("boolean", "LOCATION_ENABLED", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Location sharing (internal flavor only)
    "internalImplementation"("com.google.android.gms:play-services-location:21.1.0")

    // HTTP client for ntfy
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

- [ ] **Step 2: Build to verify flavors work**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleFdroidDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/build.gradle.kts
git commit -m "feat(location): add build flavors (fdroid/internal) and dependencies"
```

---

### Task 2: Add permissions to AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions**

Add after the existing permissions (after line 14 `RECEIVE_BOOT_COMPLETED`):

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

- [ ] **Step 2: Update service foregroundServiceType**

Change the CallMonitorService declaration from:

```xml
        <service
            android:name=".CallMonitorService"
            android:foregroundServiceType="specialUse"
            android:exported="false">
```

to:

```xml
        <service
            android:name=".CallMonitorService"
            android:foregroundServiceType="specialUse|location"
            android:exported="false">
```

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/AndroidManifest.xml
git commit -m "feat(location): add internet, location, and background location permissions"
```

---

### Task 3: Add i18n strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

- [ ] **Step 1: Add English strings**

Add before the closing `</resources>` tag in `strings.xml`:

```xml
    <!-- Location Sharing -->
    <string name="location_title">Location Sharing</string>
    <string name="location_subtitle">Share position with VIP contacts who have SOS Ring</string>
    <string name="location_your_number">Your phone number</string>
    <string name="location_your_number_hint">+39…</string>
    <string name="location_server_label">Server: %1$s</string>
    <string name="location_save">Save</string>
    <string name="location_number_saved">Number saved</string>
    <string name="location_number_invalid">Enter a valid number with country code (e.g. +39…)</string>
    <string name="location_request_sent">Location request sent to %1$s</string>
    <string name="location_pending">Getting position of %1$s…</string>
    <string name="location_received">Position of %1$s (±%2$dm)</string>
    <string name="location_no_response">No response from %1$s</string>
    <string name="location_no_number">Set your phone number first</string>
    <string name="location_perm_needed">Location permission required</string>
    <string name="location_bg_perm_needed">Background location permission required for auto-response</string>
    <string name="location_icon_desc">Request position</string>
```

- [ ] **Step 2: Add Italian strings**

Add before the closing `</resources>` tag in `strings-it.xml`:

```xml
    <!-- Location Sharing -->
    <string name="location_title">Condivisione posizione</string>
    <string name="location_subtitle">Condividi la posizione con i contatti VIP che hanno SOS Ring</string>
    <string name="location_your_number">Il tuo numero di telefono</string>
    <string name="location_your_number_hint">+39…</string>
    <string name="location_server_label">Server: %1$s</string>
    <string name="location_save">Salva</string>
    <string name="location_number_saved">Numero salvato</string>
    <string name="location_number_invalid">Inserisci un numero valido con prefisso internazionale (es. +39…)</string>
    <string name="location_request_sent">Richiesta posizione inviata a %1$s</string>
    <string name="location_pending">Ottenendo la posizione di %1$s…</string>
    <string name="location_received">Posizione di %1$s (±%2$dm)</string>
    <string name="location_no_response">Nessuna risposta da %1$s</string>
    <string name="location_no_number">Imposta prima il tuo numero di telefono</string>
    <string name="location_perm_needed">Permesso posizione necessario</string>
    <string name="location_bg_perm_needed">Permesso posizione in background necessario per la risposta automatica</string>
    <string name="location_icon_desc">Richiedi posizione</string>
```

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat(location): add i18n strings for location sharing (en + it)"
```

---

### Task 4: Modify PrefsManager for location data

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt`

- [ ] **Step 1: Add import**

Add to imports:

```kotlin
import java.security.MessageDigest
```

- [ ] **Step 2: Add companion constants**

Add inside the `companion object`, after `const val MAX_QUIET_RULES = 10`:

```kotlin
        private const val KEY_OWN_NUMBER = "own_phone_number"
        private const val KEY_OWN_TOPIC_HASH = "own_topic_hash"
        private const val KEY_NTFY_SERVER_URL = "ntfy_server_url"
        const val DEFAULT_NTFY_SERVER = "https://YOUR_NTFY_SERVER"
```

- [ ] **Step 3: Add locationEnabled to VipContact**

Change the VipContact data class from:

```kotlin
data class VipContact(val name: String, val number: String)
```

to:

```kotlin
data class VipContact(val name: String, val number: String, val locationEnabled: Boolean = false)
```

- [ ] **Step 4: Add own number and topic hash properties**

Add after `isInQuietPeriod()`:

```kotlin
    var ownPhoneNumber: String
        get() = prefs.getString(KEY_OWN_NUMBER, "") ?: ""
        set(value) {
            val normalized = normalizeNumber(value)
            prefs.edit()
                .putString(KEY_OWN_NUMBER, normalized)
                .putString(KEY_OWN_TOPIC_HASH, computeTopicHash(normalized))
                .apply()
        }

    val ownTopicHash: String
        get() = prefs.getString(KEY_OWN_TOPIC_HASH, "") ?: ""

    val ntfyServerUrl: String
        get() = prefs.getString(KEY_NTFY_SERVER_URL, DEFAULT_NTFY_SERVER) ?: DEFAULT_NTFY_SERVER

    fun computeTopicHash(normalizedNumber: String): String {
        if (normalizedNumber.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalizedNumber.toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        return "sosring-${hex.take(16)}"
    }

    fun topicHashForNumber(number: String): String {
        return computeTopicHash(normalizeNumber(number))
    }

    fun updateContactLocationEnabled(number: String, enabled: Boolean) {
        val updated = getContacts().map { c ->
            if (normalizeNumber(c.number) == normalizeNumber(number)) {
                c.copy(locationEnabled = enabled)
            } else c
        }
        saveContacts(updated)
    }
```

- [ ] **Step 5: Update saveContacts to include locationEnabled**

Replace the `saveContacts` method:

```kotlin
    fun saveContacts(contacts: List<VipContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
                put("locationEnabled", c.locationEnabled)
            })
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }
```

- [ ] **Step 6: Update getContacts to read locationEnabled**

Replace the `getContacts` method:

```kotlin
    fun getContacts(): List<VipContact> {
        val json = prefs.getString(KEY_CONTACTS, null) ?: return DEFAULT_CONTACTS
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                VipContact(
                    name = obj.getString("name"),
                    number = obj.getString("number"),
                    locationEnabled = obj.optBoolean("locationEnabled", false)
                )
            }
        } catch (e: Exception) {
            DEFAULT_CONTACTS
        }
    }
```

- [ ] **Step 7: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt
git commit -m "feat(location): add own number, topic hash, locationEnabled to PrefsManager"
```

---

### Task 5: Create NtfyClient

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/NtfyClient.kt`

- [ ] **Step 1: Create NtfyClient.kt**

```kotlin
package com.lorenzomarci.sosring

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NtfyClient(private val serverUrl: String) {

    companion object {
        private const val TAG = "NtfyClient"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun sendMessage(topic: String, message: JSONObject) {
        Thread {
            try {
                val url = "$serverUrl/$topic"
                val body = message.toString().toRequestBody(JSON_TYPE)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "ntfy POST failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ntfy POST error: ${e.message}")
            }
        }.start()
    }

    fun sendDiscovery(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "discovery")
            put("from", fromHash)
        })
    }

    fun sendDiscoveryAck(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "discovery_ack")
            put("from", fromHash)
        })
    }

    fun sendLocationRequest(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_request")
            put("from", fromHash)
            put("ts", System.currentTimeMillis() / 1000)
        })
    }

    fun sendLocationPending(topic: String, fromHash: String) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_pending")
            put("from", fromHash)
            put("ts", System.currentTimeMillis() / 1000)
        })
    }

    fun sendLocationResponse(topic: String, fromHash: String, lat: Double, lon: Double, accuracy: Float) {
        sendMessage(topic, JSONObject().apply {
            put("type", "loc_response")
            put("from", fromHash)
            put("lat", lat)
            put("lon", lon)
            put("acc", accuracy.toDouble())
            put("ts", System.currentTimeMillis() / 1000)
        })
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/NtfyClient.kt
git commit -m "feat(location): create NtfyClient for ntfy HTTP POST messaging"
```

---

### Task 6: Create LocationHelper

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/LocationHelper.kt`

- [ ] **Step 1: Create LocationHelper.kt**

```kotlin
package com.lorenzomarci.sosring

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHelper(context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val ACCURACY_THRESHOLD = 30f // meters
        private const val TIMEOUT_MS = 15_000L
    }

    interface Callback {
        fun onLocationReady(location: Location)
        fun onLocationFailed()
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var bestLocation: Location? = null
    private var isRequesting = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            Log.d(TAG, "Fix: accuracy=${location.accuracy}m")

            val best = bestLocation
            if (best == null || location.accuracy < best.accuracy) {
                bestLocation = location
            }

            if (location.accuracy <= ACCURACY_THRESHOLD) {
                Log.i(TAG, "Good fix: ${location.accuracy}m <= ${ACCURACY_THRESHOLD}m")
                deliverAndStop(location)
            }
        }
    }

    private val timeoutRunnable = Runnable {
        if (isRequesting) {
            val best = bestLocation
            if (best != null) {
                Log.i(TAG, "Timeout, using best: ${best.accuracy}m")
                deliverAndStop(best)
            } else {
                Log.w(TAG, "Timeout, no fix obtained")
                stopUpdates()
                callback?.onLocationFailed()
                callback = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestSingleFix(cb: Callback) {
        if (isRequesting) return
        callback = cb
        bestLocation = null
        isRequesting = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdates(30)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    private fun deliverAndStop(location: Location) {
        stopUpdates()
        callback?.onLocationReady(location)
        callback = null
    }

    private fun stopUpdates() {
        isRequesting = false
        handler.removeCallbacks(timeoutRunnable)
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun stop() {
        stopUpdates()
        callback = null
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Note: This will fail on `assembleFdroidDebug` because it uses `com.google.android.gms.location`. This is expected — LocationHelper is only used in internal flavor code paths guarded by `BuildConfig.LOCATION_ENABLED`.

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/LocationHelper.kt
git commit -m "feat(location): create LocationHelper with accuracy threshold and timeout"
```

---

### Task 7: Create NtfyService

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt`

- [ ] **Step 1: Create NtfyService.kt**

This is the core service: subscribes to own topic via SSE, handles incoming messages (discovery, location requests), sends automatic responses.

```kotlin
package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    companion object {
        private const val TAG = "NtfyService"
        private const val LOCATION_CHANNEL_ID = "sosring_location"
        private const val LOCATION_NOTIFICATION_ID = 3
        private const val RESPONSE_TIMEOUT_MS = 30_000L
    }

    private val prefs = PrefsManager(context)
    private lateinit var ntfyClient: NtfyClient
    private var locationHelper: LocationHelper? = null
    private var sseThread: Thread? = null
    @Volatile private var running = false
    private val pendingRequests = mutableMapOf<String, Long>() // fromHash -> timestamp
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
                .readTimeout(0, TimeUnit.SECONDS) // infinite for SSE
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
                        var line: String?
                        while (running && reader.readLine().also { line = it } != null) {
                            if (line!!.startsWith("data: ")) {
                                val data = line!!.removePrefix("data: ")
                                handleSseData(data)
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "SSE error: ${e.message}")
                    if (running) Thread.sleep(5000) // reconnect delay
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
            // ntfy SSE wraps the message in an event object
            val message = if (event.has("message")) {
                JSONObject(event.getString("message"))
            } else if (event.has("type") && event.optString("type") != "open") {
                // Direct JSON message
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
        // Check if the sender's hash matches any of our VIP contacts
        val contacts = prefs.getContacts()
        val match = contacts.any { prefs.topicHashForNumber(it.number) == fromHash }
        if (match) {
            Log.i(TAG, "Discovery from known VIP, sending ack")
            ntfyClient.sendDiscoveryAck(fromHash, prefs.ownTopicHash)
        }
    }

    private fun handleDiscoveryAck(fromHash: String) {
        // Mark the contact as location-enabled
        val contacts = prefs.getContacts()
        val contact = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        if (contact != null) {
            Log.i(TAG, "Discovery ack from ${contact.name}, enabling location")
            prefs.updateContactLocationEnabled(contact.number, true)
        }
    }

    private fun handleLocationRequest(fromHash: String) {
        if (!BuildConfig.LOCATION_ENABLED) return

        // Verify sender is a known VIP contact
        val contacts = prefs.getContacts()
        val sender = contacts.find { prefs.topicHashForNumber(it.number) == fromHash }
        if (sender == null) {
            Log.w(TAG, "Location request from unknown hash, ignoring")
            return
        }

        Log.i(TAG, "Location request from ${sender.name}, getting GPS fix...")

        // Send pending notification
        ntfyClient.sendLocationPending(fromHash, prefs.ownTopicHash)

        // Get GPS fix and respond
        locationHelper?.requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                Log.i(TAG, "Sending location: ${location.latitude},${location.longitude} acc=${location.accuracy}")
                ntfyClient.sendLocationResponse(
                    fromHash, prefs.ownTopicHash,
                    location.latitude, location.longitude, location.accuracy
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

        val lat = message.getDouble("lat")
        val lon = message.getDouble("lon")
        val acc = message.optDouble("acc", 0.0).toInt()

        // Cancel timeout
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
        ntfyClient.sendLocationRequest(targetTopic, ownHash)

        showNotification(
            context.getString(R.string.location_request_sent, contact.name),
            null
        )

        // Set timeout
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
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt
git commit -m "feat(location): create NtfyService with SSE subscription, discovery, and auto-response"
```

---

### Task 8: Integrate NtfyService into CallMonitorService

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt`

- [ ] **Step 1: Add ntfyService field**

Add after `private lateinit var notificationManager: NotificationManager`:

```kotlin
    var ntfyService: NtfyService? = null
        private set
```

- [ ] **Step 2: Start NtfyService in onCreate**

Add at the end of `onCreate()`, after `Log.i(TAG, ...)`:

```kotlin
        if (BuildConfig.LOCATION_ENABLED && prefs.ownTopicHash.isNotBlank()) {
            ntfyService = NtfyService(this).also { it.start() }
        }
```

- [ ] **Step 3: Stop NtfyService in onDestroy**

Add at the beginning of `onDestroy()`, before `stopRingtoneAndVibration()`:

```kotlin
        ntfyService?.stop()
        ntfyService = null
```

- [ ] **Step 4: Add companion accessor**

Add inside the `companion object`, after the `stop()` method:

```kotlin
        private var instance: CallMonitorService? = null

        fun getInstance(): CallMonitorService? = instance
```

- [ ] **Step 5: Set instance in onCreate/onDestroy**

Add at the start of `onCreate()`, after `super.onCreate()`:

```kotlin
        instance = this
```

Add at the end of `onDestroy()`, before `super.onDestroy()`:

```kotlin
        instance = null
```

- [ ] **Step 6: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt
git commit -m "feat(location): integrate NtfyService lifecycle into CallMonitorService"
```

---

### Task 9: Add GPS icon to VIP contact item layout

**Files:**
- Modify: `app/src/main/res/layout/item_vip_number.xml`

- [ ] **Step 1: Add GPS icon button**

Insert before `btnEdit` (before line 39):

```xml
        <ImageButton
            android:id="@+id/btnLocation"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_mylocation"
            android:contentDescription="@string/location_icon_desc"
            android:visibility="gone"
            android:layout_marginEnd="4dp" />
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/res/layout/item_vip_number.xml
git commit -m "feat(location): add GPS icon button to VIP contact item layout"
```

---

### Task 10: Update VipNumbersAdapter for GPS icon

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/VipNumbersAdapter.kt`

- [ ] **Step 1: Add location callback and update adapter**

Replace the entire `VipNumbersAdapter.kt`:

```kotlin
package com.lorenzomarci.sosring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorenzomarci.sosring.databinding.ItemVipNumberBinding

class VipNumbersAdapter(
    private val onEdit: (Int, VipContact) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onLocation: ((VipContact) -> Unit)? = null
) : ListAdapter<VipContact, VipNumbersAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<VipContact>() {
        override fun areItemsTheSame(old: VipContact, new: VipContact) =
            old.number == new.number
        override fun areContentsTheSame(old: VipContact, new: VipContact) =
            old == new
    }

    inner class ViewHolder(private val binding: ItemVipNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: VipContact, position: Int) {
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.number
            binding.btnEdit.setOnClickListener { onEdit(position, contact) }
            binding.btnDelete.setOnClickListener { onDelete(position) }

            if (BuildConfig.LOCATION_ENABLED && contact.locationEnabled && onLocation != null) {
                binding.btnLocation.visibility = View.VISIBLE
                binding.btnLocation.setOnClickListener { onLocation.invoke(contact) }
            } else {
                binding.btnLocation.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVipNumberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/VipNumbersAdapter.kt
git commit -m "feat(location): add GPS icon visibility and click handler to VipNumbersAdapter"
```

---

### Task 11: Add Location Sharing card to activity_main.xml

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Insert Location Sharing card**

Add the following card after the Quiet Hours card closing tag and before the Permissions card opening tag:

```xml
            <!-- Location Sharing -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardLocation"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                app:cardElevation="2dp"
                app:cardCornerRadius="12dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/location_title"
                        android:textSize="16sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/location_subtitle"
                        android:textSize="12sp"
                        android:textColor="?android:textColorSecondary"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <com.google.android.material.textfield.TextInputLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:layout_marginEnd="8dp">

                            <com.google.android.material.textfield.TextInputEditText
                                android:id="@+id/etOwnNumber"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:hint="@string/location_your_number_hint"
                                android:inputType="phone"
                                android:textSize="14sp" />
                        </com.google.android.material.textfield.TextInputLayout>

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnSaveNumber"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/location_save"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <TextView
                        android:id="@+id/tvLocationServer"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="11sp"
                        android:textColor="?android:textColorSecondary"
                        android:layout_marginTop="4dp" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/res/layout/activity_main.xml
git commit -m "feat(location): add Location Sharing settings card to main layout"
```

---

### Task 12: Wire up Location Sharing in MainActivity

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt`

- [ ] **Step 1: Update adapter initialization in setupRecyclerView**

Replace the `setupRecyclerView()` method:

```kotlin
    private fun setupRecyclerView() {
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> showEditDialog(position, contact) },
            onDelete = { position -> deleteContact(position) },
            onLocation = if (BuildConfig.LOCATION_ENABLED) { contact ->
                requestContactLocation(contact)
            } else null
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }
```

- [ ] **Step 2: Add location request method**

Add after `showAddQuietRuleDialog()`:

```kotlin
    private fun requestContactLocation(contact: VipContact) {
        if (prefs.ownPhoneNumber.isBlank()) {
            Toast.makeText(this, getString(R.string.location_no_number), Toast.LENGTH_SHORT).show()
            return
        }
        val ntfyService = CallMonitorService.getInstance()?.ntfyService
        if (ntfyService != null) {
            ntfyService.requestLocation(contact)
        } else {
            Toast.makeText(this, getString(R.string.grant_perms_first), Toast.LENGTH_SHORT).show()
        }
    }
```

- [ ] **Step 3: Add location settings UI wiring in setupListeners**

Add at the end of `setupListeners()`, before the closing brace:

```kotlin
        // Location sharing settings
        if (BuildConfig.LOCATION_ENABLED) {
            binding.cardLocation.visibility = android.view.View.VISIBLE
            binding.etOwnNumber.setText(prefs.ownPhoneNumber)
            binding.tvLocationServer.text = getString(R.string.location_server_label, prefs.ntfyServerUrl)

            binding.btnSaveNumber.setOnClickListener {
                val number = binding.etOwnNumber.text.toString().trim()
                if (number.startsWith("+") && number.length >= 10) {
                    prefs.ownPhoneNumber = number
                    Toast.makeText(this, getString(R.string.location_number_saved), Toast.LENGTH_SHORT).show()
                    // Restart service to connect to ntfy
                    if (prefs.isServiceEnabled) {
                        CallMonitorService.stop(this)
                        CallMonitorService.start(this)
                    }
                } else {
                    Toast.makeText(this, getString(R.string.location_number_invalid), Toast.LENGTH_LONG).show()
                }
            }
        } else {
            binding.cardLocation.visibility = android.view.View.GONE
        }
```

- [ ] **Step 4: Add discovery trigger in onResume**

Add at the end of `onResume()`:

```kotlin
        // Run discovery to refresh location-enabled contacts
        if (BuildConfig.LOCATION_ENABLED) {
            CallMonitorService.getInstance()?.ntfyService?.runDiscovery()
        }
```

- [ ] **Step 5: Add location permission request**

Add to the `runtimePermissions` build list, after the POST_NOTIFICATIONS block:

```kotlin
        if (BuildConfig.LOCATION_ENABLED) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
```

- [ ] **Step 6: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Also verify F-Droid flavor still builds:
Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleFdroidDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing
git add app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt
git commit -m "feat(location): wire up location sharing UI, discovery, and GPS request in MainActivity"
```

---

### Task 13: Final build and test

- [ ] **Step 1: Full clean build both flavors**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew clean assembleFdroidRelease assembleInternalRelease 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify F-Droid APK has no location code**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ls -la app/build/outputs/apk/fdroid/release/ app/build/outputs/apk/internal/release/`
Expected: Two separate APK files

- [ ] **Step 3: Copy internal APK to unraid**

```bash
scp /c/Users/Admin/AndroidStudioProjects/SOSRing/app/build/outputs/apk/internal/release/app-internal-release.apk unraid:/mnt/user/appdata/SOSRing-internal-release.apk
```

- [ ] **Step 4: Manual test checklist**

Install internal APK on device and verify:
1. Location Sharing card appears with number field and save button
2. Enter own number (+39...), save — toast confirms
3. Service restart connects to ntfy (check logcat for "NtfyService started")
4. GPS icon appears on VIP contacts who also have the app (after discovery)
5. Tap GPS icon — notification "Request sent" → "Getting position..." → "Position of X (±Ym)" with Maps link
6. Tap notification opens Google Maps at correct location
7. Timeout after 30s if no response

Install F-Droid APK on separate device/profile and verify:
1. Location Sharing card is NOT visible
2. No location permissions requested
3. All existing features work normally (VIP calls, quiet hours)
