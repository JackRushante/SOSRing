# Encryption, Logging & Auto-Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add E2E encryption to location responses, log location requests in-app, and implement auto-update for the internal flavor.

**Architecture:** CryptoHelper handles AES-GCM encryption with deterministic key derivation. Location logs stored in SharedPreferences (consistent with existing patterns). UpdateChecker polls version.json via Cloudflare tunnel, downloads APK, installs via FileProvider. All three features are independent and modify different parts of the codebase with minimal overlap.

**Tech Stack:** Kotlin, javax.crypto (AES-GCM), OkHttp 4.12.0, Android FileProvider, SharedPreferences JSON

---

### Task 1: CryptoHelper — E2E Encryption

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/CryptoHelper.kt`

- [ ] **Step 1: Create CryptoHelper.kt**

```kotlin
package com.lorenzomarci.sosring

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val APP_SECRET = "***REDACTED_SECRET***"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    fun deriveKey(myNumber: String, theirNumber: String): SecretKeySpec {
        val sorted = listOf(myNumber, theirNumber).sorted()
        val input = sorted[0] + sorted[1] + APP_SECRET
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String, myNumber: String, theirNumber: String): String {
        val key = deriveKey(myNumber, theirNumber)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // auto-generated 12-byte IV
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Prepend IV to ciphertext (IV + ciphertext + GCM tag)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, myNumber: String, theirNumber: String): String? {
        return try {
            val key = deriveKey(myNumber, theirNumber)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/CryptoHelper.kt
git commit -m "feat: add CryptoHelper for AES-GCM E2E encryption"
```

---

### Task 2: Wire Encryption into NtfyClient and NtfyService

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/NtfyClient.kt` (sendLocationResponse)
- Modify: `app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt` (handleLocationRequest, handleLocationResponse)

- [ ] **Step 1: Modify NtfyClient.sendLocationResponse to encrypt payload**

Replace the current `sendLocationResponse` method in `NtfyClient.kt:74-83` with:

```kotlin
fun sendLocationResponse(topic: String, fromHash: String, lat: Double, lon: Double, accuracy: Float,
                         myNumber: String, theirNumber: String) {
    val locationJson = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("acc", accuracy.toDouble())
    }.toString()
    val encrypted = CryptoHelper.encrypt(locationJson, myNumber, theirNumber)
    sendMessage(topic, JSONObject().apply {
        put("type", "loc_response")
        put("from", fromHash)
        put("enc", encrypted)
        put("ts", System.currentTimeMillis() / 1000)
    })
}
```

- [ ] **Step 2: Modify NtfyService.handleLocationRequest to pass phone numbers**

Replace the `onLocationReady` callback in `NtfyService.kt:179-185` with:

```kotlin
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
```

- [ ] **Step 3: Modify NtfyService.handleLocationResponse to decrypt payload**

Replace the `handleLocationResponse` method in `NtfyService.kt:205-230` with:

```kotlin
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
```

- [ ] **Step 4: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/NtfyClient.kt app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt
git commit -m "feat: encrypt loc_response with AES-GCM E2E"
```

---

### Task 3: Location Request Logging — Data Layer

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt`

- [ ] **Step 1: Add LocationLogEntry data class and logging methods to PrefsManager.kt**

Add after the `QuietRule` data class (line 18):

```kotlin
data class LocationLogEntry(
    val name: String,
    val number: String,
    val timestamp: Long,
    val type: String  // "incoming" or "outgoing"
)
```

Add to `PrefsManager.companion` after `KEY_NTFY_SERVER_URL`:

```kotlin
private const val KEY_LOCATION_LOGS = "location_logs"
private const val LOG_RETENTION_DAYS = 30
```

Add these methods to `PrefsManager` class, after `updateContactLocationEnabled`:

```kotlin
fun addLocationLog(name: String, number: String, type: String) {
    val logs = getLocationLogsMutable()
    logs.add(0, LocationLogEntry(name, number, System.currentTimeMillis(), type))
    // Prune entries older than 30 days
    val cutoff = System.currentTimeMillis() - LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L
    logs.removeAll { it.timestamp < cutoff }
    saveLocationLogs(logs)
}

fun getLocationLogs(): List<LocationLogEntry> {
    return getLocationLogsMutable()
}

private fun getLocationLogsMutable(): MutableList<LocationLogEntry> {
    val json = prefs.getString(KEY_LOCATION_LOGS, null) ?: return mutableListOf()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            LocationLogEntry(
                name = obj.getString("name"),
                number = obj.getString("number"),
                timestamp = obj.getLong("timestamp"),
                type = obj.getString("type")
            )
        }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }
}

private fun saveLocationLogs(logs: List<LocationLogEntry>) {
    val arr = JSONArray()
    logs.forEach { entry ->
        arr.put(JSONObject().apply {
            put("name", entry.name)
            put("number", entry.number)
            put("timestamp", entry.timestamp)
            put("type", entry.type)
        })
    }
    prefs.edit().putString(KEY_LOCATION_LOGS, arr.toString()).apply()
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt
git commit -m "feat: add location request logging to PrefsManager"
```

---

### Task 4: Wire Logging into NtfyService

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt`

- [ ] **Step 1: Add logging to handleLocationRequest (incoming)**

In `NtfyService.kt`, inside `handleLocationRequest`, after the line `Log.i(TAG, "Location request from ${sender.name}, getting GPS fix...")` (line 176), add:

```kotlin
prefs.addLocationLog(sender.name, sender.number, "incoming")
```

- [ ] **Step 2: Add logging to requestLocation (outgoing)**

In `NtfyService.kt`, inside `requestLocation`, after the line `Log.i(TAG, "Requesting location from ${contact.name}")` (line 255), add:

```kotlin
prefs.addLocationLog(contact.name, contact.number, "outgoing")
```

- [ ] **Step 3: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/NtfyService.kt
git commit -m "feat: log incoming/outgoing location requests"
```

---

### Task 5: Location Log UI — Strings and Button

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Add strings for location log (en)**

In `values/strings.xml`, before the closing `</resources>` tag, add:

```xml
    <!-- Location Log -->
    <string name="location_log_button">History</string>
    <string name="location_log_title">Location request history</string>
    <string name="location_log_empty">No recent requests</string>
    <string name="location_log_incoming">%1$s requested your location</string>
    <string name="location_log_outgoing">You requested %1$s\'s location</string>
    <string name="btn_close">Close</string>
```

- [ ] **Step 2: Add strings for location log (it)**

In `values-it/strings.xml`, before the closing `</resources>` tag, add:

```xml
    <!-- Location Log -->
    <string name="location_log_button">Cronologia</string>
    <string name="location_log_title">Cronologia richieste posizione</string>
    <string name="location_log_empty">Nessuna richiesta recente</string>
    <string name="location_log_incoming">%1$s ha richiesto la tua posizione</string>
    <string name="location_log_outgoing">Hai richiesto la posizione di %1$s</string>
    <string name="btn_close">Chiudi</string>
```

- [ ] **Step 3: Add History button to Location card in activity_main.xml**

In `activity_main.xml`, after the `tvLocationServer` TextView (line 258), before the closing `</LinearLayout>` of the Location card, add:

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnLocationLog"
                        style="@style/Widget.Material3.Button.TextButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/location_log_button"
                        android:layout_gravity="center_horizontal"
                        android:layout_marginTop="8dp" />
```

- [ ] **Step 4: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/layout/activity_main.xml
git commit -m "feat: add location log UI strings and button"
```

---

### Task 6: Location Log UI — Dialog in MainActivity

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt`

- [ ] **Step 1: Add History button listener and dialog to MainActivity**

In `setupListeners()`, inside the `if (BuildConfig.LOCATION_ENABLED)` block (after the `btnSaveNumber` listener, around line 206), add:

```kotlin
            binding.btnLocationLog.setOnClickListener {
                showLocationLogDialog()
            }
```

Add this method to `MainActivity`, after `requestContactLocation`:

```kotlin
private fun showLocationLogDialog() {
    val logs = prefs.getLocationLogs()
    val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

    val message = if (logs.isEmpty()) {
        getString(R.string.location_log_empty)
    } else {
        logs.take(50).joinToString("\n\n") { entry ->
            val date = dateFormat.format(java.util.Date(entry.timestamp))
            val desc = if (entry.type == "incoming") {
                getString(R.string.location_log_incoming, entry.name)
            } else {
                getString(R.string.location_log_outgoing, entry.name)
            }
            val arrow = if (entry.type == "incoming") "\u2B07" else "\u2B06"
            "$arrow $desc\n   $date"
        }
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.location_log_title))
        .setMessage(message)
        .setPositiveButton(getString(R.string.btn_close), null)
        .show()
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt
git commit -m "feat: add location request history dialog"
```

---

### Task 7: Auto-Update — Build Config and Manifest

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add buildConfigField and bump version in build.gradle.kts**

In `build.gradle.kts`, in the `internal` product flavor block (line 33-37), add the UPDATE_URL field:

```kotlin
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            buildConfigField("boolean", "LOCATION_ENABLED", "true")
            buildConfigField("String", "UPDATE_URL", "\"https://YOUR_NTFY_SERVER/update/\"")
        }
```

In the `fdroid` flavor (line 30-32), add a blank UPDATE_URL:

```kotlin
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "LOCATION_ENABLED", "false")
            buildConfigField("String", "UPDATE_URL", "\"\"")
        }
```

Bump version in `defaultConfig` (line 14-15):

```kotlin
        versionCode = 2
        versionName = "1.2"
```

- [ ] **Step 2: Add REQUEST_INSTALL_PACKAGES permission and FileProvider to AndroidManifest.xml**

After the existing permissions (line 19), add:

```xml
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Inside the `<application>` tag, after the `<receiver>` block (line 54), add:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Create file_paths.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk_updates" path="/" />
</paths>
```

- [ ] **Step 4: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "feat: add auto-update build config, manifest, FileProvider"
```

---

### Task 8: UpdateChecker Implementation

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/UpdateChecker.kt`

- [ ] **Step 1: Create UpdateChecker.kt**

```kotlin
package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val UPDATE_CHANNEL_ID = "sosring_updates"
        private const val UPDATE_NOTIFICATION_ID = 4
        private const val APK_FILENAME = "SOSRing-update.apk"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkAndNotify() {
        if (BuildConfig.UPDATE_URL.isBlank()) return

        Thread {
            try {
                val versionUrl = "${BuildConfig.UPDATE_URL}version.json"
                val request = Request.Builder().url(versionUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Version check failed: ${response.code}")
                    response.close()
                    return@Thread
                }

                val json = JSONObject(response.body!!.string())
                response.close()

                val remoteCode = json.getInt("versionCode")
                val remoteName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")

                val currentCode = BuildConfig.VERSION_CODE

                if (remoteCode > currentCode) {
                    Log.i(TAG, "Update available: v$remoteName (code $remoteCode > $currentCode)")
                    showUpdateNotification(remoteName, apkUrl)
                } else {
                    Log.d(TAG, "App is up to date (code $currentCode >= $remoteCode)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check error: ${e.message}")
            }
        }.start()
    }

    private fun showUpdateNotification(versionName: String, apkUrl: String) {
        createUpdateChannel()

        val downloadIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE"
            putExtra("apk_url", apkUrl)
            putExtra("version_name", versionName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, UPDATE_NOTIFICATION_ID, downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SOS Ring")
            .setContentText(context.getString(R.string.update_available, versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show update notification: ${e.message}")
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        Thread {
            try {
                val fullUrl = if (apkUrl.startsWith("http")) apkUrl
                    else "${BuildConfig.UPDATE_URL}$apkUrl"

                Log.i(TAG, "Downloading APK: $fullUrl")
                val request = Request.Builder().url(fullUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "APK download failed: ${response.code}")
                    response.close()
                    return@Thread
                }

                val apkFile = File(context.cacheDir, APK_FILENAME)
                FileOutputStream(apkFile).use { fos ->
                    response.body!!.byteStream().copyTo(fos)
                }
                response.close()

                Log.i(TAG, "APK downloaded: ${apkFile.length()} bytes")

                val apkUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(installIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Download/install error: ${e.message}")
            }
        }.start()
    }

    private fun createUpdateChannel() {
        val channel = android.app.NotificationChannel(
            UPDATE_CHANNEL_ID,
            "App Updates",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for app updates"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }
}
```

- [ ] **Step 2: Add update strings (en) to values/strings.xml**

Before the closing `</resources>` tag:

```xml
    <!-- Auto-Update -->
    <string name="update_available">Update available: v%1$s — tap to install</string>
    <string name="update_downloading">Downloading update…</string>
```

- [ ] **Step 3: Add update strings (it) to values-it/strings.xml**

Before the closing `</resources>` tag:

```xml
    <!-- Auto-Update -->
    <string name="update_available">Aggiornamento disponibile: v%1$s — tocca per installare</string>
    <string name="update_downloading">Download aggiornamento…</string>
```

- [ ] **Step 4: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/UpdateChecker.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: add UpdateChecker with download and install"
```

---

### Task 9: Wire Auto-Update into CallMonitorService and MainActivity

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt`
- Modify: `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt`

- [ ] **Step 1: Add 12-hour update check loop to CallMonitorService**

In `CallMonitorService.kt`, add a field after `ntfyService` (line 51):

```kotlin
    private var updateChecker: UpdateChecker? = null
    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateInterval = 12 * 60 * 60 * 1000L // 12 hours
```

In `onCreate()`, after the ntfyService block (after line 116), add:

```kotlin
        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            updateChecker = UpdateChecker(this)
            scheduleUpdateCheck()
        }
```

Add the scheduling method after `onStartCommand`:

```kotlin
    private fun scheduleUpdateCheck() {
        updateHandler.postDelayed(object : Runnable {
            override fun run() {
                updateChecker?.checkAndNotify()
                if (instance != null) {
                    updateHandler.postDelayed(this, updateInterval)
                }
            }
        }, updateInterval)
    }
```

In `onDestroy()`, before `ntfyService?.stop()` (line 120), add:

```kotlin
        updateHandler.removeCallbacksAndMessages(null)
```

- [ ] **Step 2: Add update check on app open and handle download intent in MainActivity**

In `MainActivity.onResume()`, after the discovery block (after line 105), add:

```kotlin
        // Check for updates (internal flavor only)
        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            UpdateChecker(this).checkAndNotify()
        }
```

In `MainActivity.onCreate()`, after `loadQuietRules()` (line 88), add:

```kotlin
        handleUpdateIntent(intent)
```

Add the intent handler method after `loadQuietRules`:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action == "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE") {
            val apkUrl = intent.getStringExtra("apk_url") ?: return
            val versionName = intent.getStringExtra("version_name") ?: ""
            Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
            UpdateChecker(this).downloadAndInstall(apkUrl)
        }
    }
```

- [ ] **Step 3: Verify build compiles**

Run: `cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew compileInternalReleaseKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt
git commit -m "feat: wire auto-update into service (12h) and activity (on open)"
```

---

### Task 10: Server-Side Setup on Unraid

**Files:** none (server config)

- [ ] **Step 1: Create update directory and version.json on unraid**

```bash
ssh unraid "mkdir -p /mnt/user/appdata/ntfy/update && cat > /mnt/user/appdata/ntfy/update/version.json << 'EOF'
{
  \"versionCode\": 2,
  \"versionName\": \"1.2\",
  \"apkUrl\": \"SOSRing-internal-release.apk\"
}
EOF"
```

- [ ] **Step 2: Configure ntfy container to serve static files from /update/ path**

The ntfy container needs to serve the `/update/` directory. Two options:

**Option A (recommended):** Add a volume mount to the ntfy container in Docker:
```bash
ssh unraid "docker stop ntfy && docker run -d --name ntfy --restart=unless-stopped \
  -v /mnt/user/appdata/ntfy:/etc/ntfy \
  -v /mnt/user/appdata/ntfy/cache:/var/cache/ntfy \
  -v /mnt/user/appdata/ntfy/update:/var/www/update \
  --network servarrnetwork \
  -p 2586:80 \
  binwiederhier/ntfy serve"
```

**Option B:** Use ntfy's built-in attachment/web root. Check current docker compose/run config first.

Verify the endpoint is reachable:
```bash
curl -s https://YOUR_NTFY_SERVER/update/version.json
```
Expected: the JSON content with versionCode 2.

- [ ] **Step 3: Copy release APK to server after building**

After building the APK locally:
```bash
scp app/build/outputs/apk/internal/release/app-internal-release.apk unraid:/mnt/user/appdata/ntfy/update/SOSRing-internal-release.apk
```

---

### Task 11: Build, Sign, and Deploy

**Files:** none (build/deploy)

- [ ] **Step 1: Build both flavors**

```bash
cd C:/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleFdroidRelease assembleInternalRelease
```

Expected: BUILD SUCCESSFUL, APKs in `app/build/outputs/apk/`

- [ ] **Step 2: Copy APKs to unraid**

```bash
scp app/build/outputs/apk/internal/release/app-internal-release.apk unraid:/mnt/user/appdata/SOSRing-internal-release.apk
scp app/build/outputs/apk/internal/release/app-internal-release.apk unraid:/mnt/user/appdata/ntfy/update/SOSRing-internal-release.apk
scp app/build/outputs/apk/fdroid/release/app-fdroid-release.apk unraid:/mnt/user/appdata/SOSRing-release.apk
```

- [ ] **Step 3: Verify auto-update endpoint**

```bash
curl -s https://YOUR_NTFY_SERVER/update/version.json | python3 -m json.tool
```

Expected:
```json
{
    "versionCode": 2,
    "versionName": "1.2",
    "apkUrl": "SOSRing-internal-release.apk"
}
```

- [ ] **Step 4: Commit all changes and tag**

```bash
git add -A && git commit -m "chore: v1.2 release — encryption, logging, auto-update"
git tag v1.2
git push origin main --tags
```
