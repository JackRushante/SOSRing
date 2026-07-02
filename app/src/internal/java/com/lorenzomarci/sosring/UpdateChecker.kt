package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val UPDATE_CHANNEL_ID = "sosring_updates"
        private const val UPDATE_NOTIFICATION_ID = 4
        private const val DOWNLOAD_NOTIFICATION_ID = 5
        private const val APK_FILENAME = "SOSRing-update.apk"
        private const val CHECK_THROTTLE_MS = 5 * 60 * 1000L
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }

    fun checkAndNotify(force: Boolean = false) {
        if (BuildConfig.UPDATE_URL.isBlank()) return

        if (!force) {
            val prefs = context.getSharedPreferences("sosring_prefs", Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
            if (System.currentTimeMillis() - lastCheck < CHECK_THROTTLE_MS) {
                Log.d(TAG, "Skipping update check — last check was recent")
                return
            }
        }

        Thread {
            try {
                context.getSharedPreferences("sosring_prefs", Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()

                val versionUrl = UpdateUrlPolicy.resolveVersionUrl(BuildConfig.UPDATE_URL) ?: return@Thread
                val request = Request.Builder().url(versionUrl).build()
                NetworkClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Version check failed: ${response.code}")
                        return@Thread
                    }

                    val body = response.body?.string() ?: return@Thread
                    val json = JSONObject(body)

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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check error: ${e.message}")
            }
        }.start()
    }

    private fun showUpdateNotification(versionName: String, apkUrl: String) {
        createUpdateChannel()

        val token = java.util.UUID.randomUUID().toString()
        PrefsManager(context).pendingUpdateToken = token

        val downloadIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE"
            putExtra("apk_url", apkUrl)
            putExtra("version_name", versionName)
            putExtra("update_token", token)
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
                val fullUrl = UpdateUrlPolicy.resolveApkUrl(BuildConfig.UPDATE_URL, apkUrl) ?: run {
                    Log.e(TAG, "Invalid APK update URL")
                    return@Thread
                }

                createUpdateChannel()
                NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID)
                showDownloadProgress(0, indeterminate = true)

                Log.i(TAG, "Downloading APK: $fullUrl")
                val apkDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
                val apkFile = File(apkDir, APK_FILENAME)
                val apkRequest = Request.Builder().url(fullUrl).build()
                NetworkClient.client.newCall(apkRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "APK download failed: ${response.code}")
                        showDownloadFailed()
                        return@Thread
                    }
                    val body = response.body ?: run {
                        showDownloadFailed()
                        return@Thread
                    }
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { fos ->
                            val buffer = ByteArray(64 * 1024)
                            var readTotal = 0L
                            var lastPct = -1
                            var read: Int
                            while (input.read(buffer).also { read = it } >= 0) {
                                if (read > 0) {
                                    fos.write(buffer, 0, read)
                                    readTotal += read
                                    if (total > 0) {
                                        val pct = (readTotal * 100 / total).toInt()
                                        if (pct >= lastPct + 2 || pct == 100) {
                                            lastPct = pct
                                            showDownloadProgress(pct)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "APK downloaded: ${apkFile.length()} bytes")

                val shaUrl = "$fullUrl.sha256"
                val expectedSha = try {
                    val shaRequest = Request.Builder().url(shaUrl).build()
                    NetworkClient.client.newCall(shaRequest).execute().use { it.body?.string()?.trim() }
                } catch (e: Exception) {
                    Log.e(TAG, "SHA-256 file not available, refusing update: ${e.message}")
                    null
                }

                val actualSha = sha256OfFile(apkFile)
                if (!ApkIntegrityPolicy.isVerified(expectedSha, actualSha)) {
                    Log.e(TAG, "APK SHA-256 verification failed: expected=$expectedSha actual=$actualSha")
                    apkFile.delete()
                    showDownloadFailed()
                    return@Thread
                }
                Log.i(TAG, "APK SHA-256 verified: $actualSha")

                NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)

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
                showDownloadFailed()
            }
        }.start()
    }

    private fun showDownloadProgress(percent: Int, indeterminate: Boolean = false) {
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.update_downloading))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, percent, indeterminate)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show download notification: ${e.message}")
        }
    }

    private fun showDownloadFailed() {
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.update_download_failed))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show download-failed notification: ${e.message}")
        }
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createUpdateChannel() {
        val channel = android.app.NotificationChannel(
            UPDATE_CHANNEL_ID,
            context.getString(R.string.notif_update_channel_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_update_channel_desc)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }
}
