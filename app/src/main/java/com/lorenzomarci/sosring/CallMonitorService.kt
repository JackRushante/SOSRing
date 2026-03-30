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

        private var instance: CallMonitorService? = null
        fun getInstance(): CallMonitorService? = instance
    }

    private lateinit var prefs: PrefsManager
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    var ntfyService: NtfyService? = null
        private set

    private var updateChecker: UpdateChecker? = null
    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateInterval = 12 * 60 * 60 * 1000L // 12 hours

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
                    if (number != null) {
                        val vipContact = findVipContact(number)
                        if (vipContact != null && !prefs.isInQuietPeriod()) {
                            if (vipContact.ringtoneEnabled && !prefs.isMuted) {
                                Log.i(TAG, "VIP call detected! Overriding audio.")
                                overrideAudio()
                            } else {
                                Log.i(TAG, "VIP call detected but ringtone disabled or muted. Skipping override.")
                            }
                        }
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
        instance = this
        prefs = PrefsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification())

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneReceiver, filter)

        Log.i(TAG, "Service started. Monitoring ${prefs.getVipNumbers().size} VIP numbers.")

        if (BuildConfig.LOCATION_ENABLED && prefs.ownTopicHash.isNotBlank()) {
            ntfyService = NtfyService(this).also { it.start() }
        }

        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            updateChecker = UpdateChecker(this)
            scheduleUpdateCheck()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

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

    override fun onDestroy() {
        updateHandler.removeCallbacksAndMessages(null)
        ntfyService?.stop()
        ntfyService = null
        stopRingtoneAndVibration()
        if (isOverriding) restoreAudio()
        unregisterReceiver(phoneReceiver)
        Log.i(TAG, "Service stopped.")
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun findVipContact(incoming: String): VipContact? {
        return prefs.findVipContact(incoming)
    }

    @Suppress("DEPRECATION")
    private fun overrideAudio() {
        if (isOverriding) return

        // Feature 1: Skip override if phone is already in normal ringer mode
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            Log.i(TAG, "Phone already in NORMAL mode, skipping override.")
            return
        }

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

            // 2. Set ONLY alarm volume (our MediaPlayer uses ALARM stream)
            // Do NOT change ringer mode — that would trigger system ringtone
            val volumePercent = prefs.volumePercent
            val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val alarmTarget = (alarmMax * volumePercent / 100).coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmTarget, 0)
            Log.d(TAG, "Alarm volume set to $volumePercent%: alarm=$alarmTarget")

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
            val soundType = if (prefs.overrideSoundType == PrefsManager.SOUND_TYPE_NOTIFICATION) {
                RingtoneManager.TYPE_NOTIFICATION
            } else {
                RingtoneManager.TYPE_RINGTONE
            }
            val ringtoneUri = RingtoneManager.getDefaultUri(soundType)
            Log.d(TAG, "Sound URI ($soundType): $ringtoneUri")
            val vol = prefs.volumePercent / 100f
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@CallMonitorService, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = prefs.overrideSoundType != PrefsManager.SOUND_TYPE_NOTIFICATION
                setVolume(vol, vol)
                prepare()
                start()
            }
            Log.i(TAG, "Sound PLAYING on ALARM stream at ${prefs.volumePercent}% volume.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: ${e.message}", e)
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

        // 2. Restore alarm volume (only stream we changed)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
        Log.d(TAG, "Alarm volume restored to $savedAlarmVolume")

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
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text, count))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildOverrideNotification(): Notification {
        return NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_override_title))
            .setContentText(getString(R.string.notif_override_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }
}
