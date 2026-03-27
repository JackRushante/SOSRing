package com.lorenzomarci.sosring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
