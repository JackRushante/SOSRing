package com.lorenzomarci.sosring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat

class CallMonitorService : Service() {

    companion object {
        private const val TAG = "SOSRing"
        private const val CHANNEL_ID = "sosring_channel"
        private const val NOTIFICATION_ID = 1
        private const val OVERRIDE_CHANNEL_ID = "sosring_override"
        private const val OVERRIDE_NOTIFICATION_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitorService::class.java))
        }
    }

    private lateinit var prefs: PrefsManager
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    private var isOverriding = false
    private var savedRingerMode = AudioManager.RINGER_MODE_NORMAL
    private var savedRingVolume = 0
    private var savedNotifVolume = 0
    private var savedAlarmVolume = 0
    private var savedDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var savedVibrateRing = AudioManager.VIBRATE_SETTING_OFF
    private var savedVibrateNotif = AudioManager.VIBRATE_SETTING_OFF

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            Log.d(TAG, "Phone state: $state, number: ${number?.take(4)}***")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (number != null && isVipNumber(number)) {
                        Log.i(TAG, "VIP call detected! Overriding audio.")
                        overrideAudio()
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isOverriding) {
                        Log.i(TAG, "Call ended. Restoring audio.")
                        restoreAudio()
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call answered — stop ringtone/vibration, restore audio on IDLE
                    if (isOverriding) {
                        Log.i(TAG, "Call answered. Stopping ringtone.")
                        stopRingtoneAndVibration()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification())

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneReceiver, filter)

        Log.i(TAG, "Service started. Monitoring ${prefs.getVipNumbers().size} VIP numbers.")
    }

    override fun onDestroy() {
        stopRingtoneAndVibration()
        if (isOverriding) restoreAudio()
        unregisterReceiver(phoneReceiver)
        Log.i(TAG, "Service stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun isVipNumber(incoming: String): Boolean {
        val normalized = prefs.normalizeNumber(incoming)
        return prefs.getVipNumbers().any { vip ->
            PhoneNumberUtils.compare(normalized, vip)
        }
    }

    @Suppress("DEPRECATION")
    private fun overrideAudio() {
        if (isOverriding) return

        try {
            // Save ALL current audio state
            savedRingerMode = audioManager.ringerMode
            savedRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            savedNotifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            savedDndFilter = notificationManager.currentInterruptionFilter
            savedVibrateRing = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER)
            savedVibrateNotif = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION)

            Log.d(TAG, "Saved state: ringer=$savedRingerMode, ringVol=$savedRingVolume, " +
                    "notifVol=$savedNotifVolume, alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

            // 1. Override DND FIRST
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Log.d(TAG, "DND overridden to FILTER_ALL")
            } else {
                Log.w(TAG, "DND permission NOT granted!")
            }

            // 2. Ringer mode to NORMAL
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            Log.d(TAG, "Ringer set to NORMAL")

            // 3. Calculate target volume from user preference (50-100%)
            val volumePercent = prefs.volumePercent
            fun targetVolume(stream: Int): Int {
                val max = audioManager.getStreamMaxVolume(stream)
                return (max * volumePercent / 100).coerceAtLeast(1)
            }

            // 4. Set ring, notification, alarm volumes to configured level
            val ringTarget = targetVolume(AudioManager.STREAM_RING)
            val notifTarget = targetVolume(AudioManager.STREAM_NOTIFICATION)
            val alarmTarget = targetVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, ringTarget, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifTarget, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmTarget, 0)
            Log.d(TAG, "Volumes set to $volumePercent%: ring=$ringTarget, notif=$notifTarget, alarm=$alarmTarget")

        } catch (e: Exception) {
            Log.e(TAG, "Error during audio override: ${e.message}", e)
        }

        // 6. Play ringtone via ALARM stream (bypasses ALL DND restrictions)
        startRingtone()

        // 7. Start vibration
        startVibration()

        isOverriding = true
        notificationManager.notify(OVERRIDE_NOTIFICATION_ID, buildOverrideNotification())
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            Log.d(TAG, "Ringtone URI: $ringtoneUri")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@CallMonitorService, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "Ringtone PLAYING on ALARM stream.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone: ${e.message}", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // Pattern: wait 0ms, vibrate 500ms, pause 500ms — repeating
            val pattern = longArrayOf(0, 500, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            Log.i(TAG, "Vibration started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopRingtoneAndVibration() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ringtone: ${e.message}")
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        if (!isOverriding) return

        Log.d(TAG, "Restoring state: ringer=$savedRingerMode, ringVol=$savedRingVolume, " +
                "notifVol=$savedNotifVolume, alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

        // 1. Stop our ringtone and vibration
        stopRingtoneAndVibration()

        // 2. Restore ringer mode FIRST (on OnePlus/Realme, SILENT mode auto-enables DND)
        audioManager.ringerMode = savedRingerMode
        Log.d(TAG, "Ringer restored to $savedRingerMode")

        // 3. Restore ALL volumes (including alarm)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingVolume, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVolume, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)

        // 4. Restore DND LAST — this overrides any DND change triggered by ringerMode
        //    Small delay to let the system settle after ringer mode change
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(savedDndFilter)
                Log.d(TAG, "DND restored to $savedDndFilter (delayed)")
            }
        }, 200)

        isOverriding = false
        notificationManager.cancel(OVERRIDE_NOTIFICATION_ID)
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Monitoraggio chiamate",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifica persistente del servizio SOS Ring"
        }

        val overrideChannel = NotificationChannel(
            OVERRIDE_CHANNEL_ID,
            "Override attivo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifica quando la suoneria viene forzata"
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(overrideChannel)
    }

    private fun buildPersistentNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val count = prefs.getContacts().size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Ring attivo")
            .setContentText("Monitoraggio $count contatti VIP")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildOverrideNotification(): Notification {
        return NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle("Suoneria forzata!")
            .setContentText("Chiamata VIP in arrivo — suoneria attivata")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }
}
