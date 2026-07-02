package com.lorenzomarci.sosring

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.ServiceInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class CallMonitorService : Service() {

    companion object {
        private const val TAG = "SOSRing"
        private const val CHANNEL_ID = "sosring_channel"
        private const val NOTIFICATION_ID = 1
        private const val OVERRIDE_CHANNEL_ID = "sosring_override"
        private const val OVERRIDE_NOTIFICATION_ID = 2
        private const val PERM_WARNING_NOTIFICATION_ID = 5

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

    var pushEngine: PushEngine? = null
        private set

    private var updateChecker: UpdateChecker? = null
    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateInterval = 12 * 60 * 60 * 1000L // 12 hours

    private var isOverriding = false
    private var savedAlarmVolume = 0
    private var savedDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var currentRingingNumber: String? = null

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val numberHash = number?.let {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(it.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .take(8)
            }
            Log.d(TAG, "Phone state: $state, hash: $numberHash")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    this@CallMonitorService.warnIfCriticalPermsMissing()
                    if (number != null) {
                        val vipContact = findVipContact(number)
                        if (vipContact != null && !prefs.isInQuietPeriod()) {
                            if (!prefs.isMuted) {
                                val shouldOverride = AudioOverridePolicy.shouldOverride(
                                    audioManager.ringerMode,
                                    audioManager.getStreamVolume(AudioManager.STREAM_RING),
                                    notificationManager.currentInterruptionFilter
                                )
                                if (shouldOverride) {
                                    Log.i(TAG, "VIP call detected! Overriding audio.")
                                    overrideAudio(number)
                                } else {
                                    Log.i(TAG, "VIP call detected but system already audible. Skipping override.")
                                }
                            } else {
                                Log.i(TAG, "VIP call detected but muted. Skipping override.")
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

        restoreStaleOverrideState()

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneReceiver, filter)

        if (isOverriding && isCallStateIdle()) {
            Log.i(TAG, "Call went idle before receiver registration; restoring audio.")
            restoreAudio()
        }

        Log.i(TAG, "Service started. Monitoring ${prefs.getVipNumbers().size} VIP numbers.")

        if (Push.canStart(this)) {
            Push.start(this)
            pushEngine = Push.engine()
        }

        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            updateChecker = UpdateChecker(this)
            scheduleUpdateCheck()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun setLocationForegroundType(active: Boolean) {
        val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val type = if (active && hasLocation) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        try {
            startForeground(NOTIFICATION_ID, buildPersistentNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FGS type: ${e.message}")
        }
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
        pushEngine?.stop()
        pushEngine = null
        stopRingtoneAndVibration()
        if (isOverriding) restoreAudio()
        unregisterReceiver(phoneReceiver)
        Log.i(TAG, "Service stopped.")
        instance = null
        currentRingingNumber = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStaleOverrideState() {
        val persistedOverriding = prefs.overrideActive
        if (!persistedOverriding) return

        savedAlarmVolume = prefs.savedAlarmVolume
        savedDndFilter = prefs.savedDndFilter
        isOverriding = true

        if (OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding, isCallStateIdle())) {
            Log.i(TAG, "Self-healing stale override state after process restart.")
            restoreAudio()
        } else {
            Log.i(TAG, "Call still active after process restart; deferring restore of stale override state.")
        }
    }

    @Suppress("DEPRECATION")
    private fun isCallStateIdle(): Boolean {
        return try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read call state: ${e.message}")
            false
        }
    }

    private fun warnIfCriticalPermsMissing() {
        if (!prefs.isServiceEnabled) return
        val callLogOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val phoneStateOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val dndOk = notificationManager.isNotificationPolicyAccessGranted
        val missing = PermissionHealth.criticalMissing(callLogOk, phoneStateOk, dndOk)
        if (missing.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!WarnThrottle.shouldWarn(prefs.lastPermWarningMs, now)) return
        prefs.lastPermWarningMs = now
        val openIntent = PendingIntent.getActivity(
            this, PERM_WARNING_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.perm_revoked_notif_title))
            .setContentText(getString(R.string.perm_revoked_notif_text))
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(PERM_WARNING_NOTIFICATION_ID, notif)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post permission warning: ${e.message}")
        }
    }

    private fun findVipContact(incoming: String): VipContact? {
        return prefs.findVipContact(incoming)
    }

    @Suppress("DEPRECATION")
    private fun overrideAudio(number: String) {
        if (isOverriding) return

        if (!AudioOverridePolicy.shouldOverride(audioManager.ringerMode, audioManager.getStreamVolume(AudioManager.STREAM_RING), notificationManager.currentInterruptionFilter)) {
            Log.i(TAG, "Phone already allows calls, skipping override.")
            return
        }

        currentRingingNumber = number

        try {
            // Save the state changed by SOS Ring.
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            savedDndFilter = notificationManager.currentInterruptionFilter

            Log.d(TAG, "Saved state: alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

            // 1. Override DND FIRST
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Log.d(TAG, "DND overridden to FILTER_ALL")
            } else {
                Log.w(TAG, "DND permission NOT granted!")
                notificationManager.notify(
                    OVERRIDE_NOTIFICATION_ID + 1,
                    NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
                        .setContentTitle(getString(R.string.notif_override_title))
                        .setContentText(getString(R.string.dnd_revoked_warning))
                        .setSmallIcon(R.drawable.ic_notification_sos)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                )
            }

            val volumePercent = prefs.volumePercent
            val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val alarmTarget = (alarmMax * volumePercent / 100).coerceAtLeast(1)
            val alarmCurrent = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val alarmFinal = maxOf(alarmTarget, alarmCurrent)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmFinal, 0)
            Log.d(TAG, "Override playback: alarm volume=$alarmFinal (app=$alarmTarget, system=$alarmCurrent)")

        } catch (e: Exception) {
            Log.e(TAG, "Error during audio override: ${e.message}", e)
        }

        startOverrideSound()

        // Start vibration
        startVibration()

        isOverriding = true
        prefs.savedAlarmVolume = savedAlarmVolume
        prefs.savedDndFilter = savedDndFilter
        prefs.overrideActive = true
        notificationManager.notify(OVERRIDE_NOTIFICATION_ID, buildOverrideNotification())
    }

    private fun startOverrideSound() {
        val customUri = currentRingingNumber?.let {
            ContactRingtoneHelper.getRingtoneUri(this, it)
        }

        val useNotificationSound = prefs.overrideSoundType == PrefsManager.SOUND_TYPE_NOTIFICATION
        val defaultUri = RingtoneManager.getDefaultUri(
            if (useNotificationSound) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_RINGTONE
        )
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val candidates = listOfNotNull(
            customUri?.let { it to "custom" },
            defaultUri?.let { it to "default ringtone" },
            alarmUri?.let { it to "alarm" }
        )
        playCandidate(candidates, 0)
    }

    private fun playCandidate(candidates: List<Pair<Uri, String>>, index: Int) {
        if (index >= candidates.size) {
            Log.e(TAG, "Failed to play override sound: all URIs failed")
            return
        }

        val (uri, label) = candidates[index]
        Log.d(TAG, "Override sound URI: $uri ($label)")

        val vol = 1f
        val mp = MediaPlayer()
        try {
            mp.setDataSource(this, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = prefs.overrideSoundType != PrefsManager.SOUND_TYPE_NOTIFICATION
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { player ->
                if (mediaPlayer !== player) return@setOnPreparedListener
                try {
                    player.start()
                    Log.i(TAG, "Override sound playing on ALARM stream at ${prefs.volumePercent}%.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start prepared override sound: ${e.message}")
                }
            }
            mp.setOnErrorListener { player, what, extra ->
                Log.e(TAG, "MediaPlayer error preparing override sound: what=$what extra=$extra")
                if (mediaPlayer === player) {
                    mediaPlayer = null
                    try { player.release() } catch (_: Exception) {}
                    Log.w(TAG, "Override sound ($label) failed asynchronously, advancing fallback chain.")
                    playCandidate(candidates, index + 1)
                } else {
                    try { player.release() } catch (_: Exception) {}
                }
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play URI: ${e.message}")
            try { mp.release() } catch (_: Exception) {}
            playCandidate(candidates, index + 1)
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
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ringtone: ${e.message}")
            } finally {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing ringtone: ${e.message}")
                }
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        if (!isOverriding) return

        Log.d(TAG, "Restoring state: alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

        // 1. Stop our sound and vibration
        stopRingtoneAndVibration()

        try {
            // 2. Restore the only stream modified by SOS Ring playback.
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            Log.d(TAG, "Alarm volume restored to $savedAlarmVolume")

            // Restore DND synchronously (no ringer-mode change happens anymore)
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(savedDndFilter)
                Log.d(TAG, "DND restored to $savedDndFilter")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio state: ${e.message}", e)
        }

        isOverriding = false
        prefs.overrideActive = false
        notificationManager.cancel(OVERRIDE_NOTIFICATION_ID)
        currentRingingNumber = null
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_service_channel_desc)
        }

        val overrideChannel = NotificationChannel(
            OVERRIDE_CHANNEL_ID,
            getString(R.string.notif_override_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_override_channel_desc)
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
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    fun refreshNotification() {
        if (!::notificationManager.isInitialized) return
        try {
            notificationManager.notify(NOTIFICATION_ID, buildPersistentNotification())
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot refresh notification: ${e.message}")
        }
    }

    private fun buildOverrideNotification(): Notification {
        return NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_override_title))
            .setContentText(getString(R.string.notif_override_text))
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }
}
