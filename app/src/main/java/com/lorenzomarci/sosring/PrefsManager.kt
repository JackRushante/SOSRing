package com.lorenzomarci.sosring

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

data class VipContact(val name: String, val number: String, val locationEnabled: Boolean = false,
    val ringtoneEnabled: Boolean = true, val callAlertMode: CallAlertMode = CallAlertMode.INHERIT)

data class QuietRule(
    val days: Set<Int>,    // Calendar.MONDAY(2)..SUNDAY(1)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

data class LocationLogEntry(
    val name: String,
    val number: String,
    val timestamp: Long,
    val type: String  // "incoming" or "outgoing"
)

enum class AppPalette {
    INDACO,
    TEAL,
    ARGILLA,
    ARDESIA;

    companion object {
        fun fromStoredOrdinal(value: Int): AppPalette = values().getOrElse(value) { INDACO }
    }
}

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sosring_prefs", Context.MODE_PRIVATE)
    private val callAttempts = context.getSharedPreferences("sosring_call_attempts", Context.MODE_PRIVATE)

    private fun setCallPreference(key: String, value: Any) {
        if (prefs.all[key] == value) return
        // Invalidate persisted attempts even if the monitoring service is not currently running.
        callAttempts.edit().clear().apply()
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }

    companion object {
        private const val KEY_CONTACTS = "vip_contacts"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VOLUME_PERCENT = "volume_percent"
        const val MIN_VOLUME_PERCENT = 25
        const val MAX_VOLUME_PERCENT = 100
        const val DEFAULT_VOLUME_PERCENT = 100

        private const val KEY_MESSAGE_VOLUME_PERCENT = "message_volume_percent"
        const val MIN_MESSAGE_VOLUME_PERCENT = 5
        const val DEFAULT_MESSAGE_VOLUME_PERCENT = 100
        private const val KEY_MESSAGE_SOUND_ENABLED = "message_sound_enabled"
        const val DEFAULT_MESSAGE_SOUND_ENABLED = true
        private const val KEY_MESSAGE_SOUND_TYPE = "message_sound_type"
        const val MESSAGE_SOUND_DEFAULT = 0
        const val MESSAGE_SOUND_CONTACT = 1

        private const val KEY_MUTE_UNTIL = "mute_until_timestamp"
        private const val KEY_OVERRIDE_SOUND_TYPE = "override_sound_type"
        const val SOUND_TYPE_RINGTONE = 0
        const val SOUND_TYPE_NOTIFICATION = 1

        val DEFAULT_CONTACTS = emptyList<VipContact>()

        private const val KEY_QUIET_RULES = "quiet_rules"
        const val MAX_QUIET_RULES = 10

        private const val KEY_OWN_NUMBER = "own_phone_number"
        private const val KEY_NTFY_SERVER_URL = "ntfy_server_url"
        val DEFAULT_NTFY_SERVER = BuildConfig.NTFY_SERVER

        private const val KEY_NTFY_AUTH_TOKEN = "ntfy_auth_token"
        val DEFAULT_NTFY_AUTH_TOKEN = BuildConfig.NTFY_AUTH_TOKEN

        private const val KEY_USER_PASSPHRASE = "user_passphrase"
        private const val KEY_PASSPHRASE_CREATED_AT = "passphrase_created_at"

        private const val KEY_LOCATION_LOGS = "location_logs"
        private const val LOG_RETENTION_DAYS = 30

        private const val KEY_LAST_PERM_WARNING = "last_perm_warning"

        private const val KEY_OVERRIDE_ACTIVE = "override_active"
        private const val KEY_SAVED_ALARM_VOLUME = "saved_alarm_volume"
        private const val KEY_SAVED_DND_FILTER = "saved_dnd_filter"

        private const val KEY_PENDING_UPDATE_TOKEN = "pending_update_token"

        private const val KEY_THEME_PALETTE = "theme_palette"
        private const val KEY_THEME_MODE = "theme_mode"

        fun applyLocationEnabledUpdate(contacts: List<VipContact>, number: String, enabled: Boolean): List<VipContact> =
            contacts.map { c -> if (PhoneUtils.matches(c.number, number)) c.copy(locationEnabled = enabled) else c }
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = setCallPreference(KEY_SERVICE_ENABLED, value)

    var themePalette: AppPalette
        get() = AppPalette.fromStoredOrdinal(prefs.getInt(KEY_THEME_PALETTE, AppPalette.INDACO.ordinal))
        set(value) = prefs.edit().putInt(KEY_THEME_PALETTE, value.ordinal).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).let { stored ->
            when (stored) {
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> stored
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
        set(value) {
            val safeValue = when (value) {
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> value
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt(KEY_THEME_MODE, safeValue).apply()
        }

    var volumePercent: Int
        get() = prefs.getInt(KEY_VOLUME_PERCENT, DEFAULT_VOLUME_PERCENT)
        set(value) = setCallPreference(KEY_VOLUME_PERCENT, value.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT))

    var callAlertMode: CallAlertMode
        get() = CallAlertMode.parse(prefs.getString("call_alert_mode", null)).let {
            if (it == CallAlertMode.INHERIT) CallAlertMode.FIRST else it
        }
        set(value) = setCallPreference("call_alert_mode", value.name)

    var repeatCallWindowMinutes: Int
        get() = RepeatCallPolicy.windowMinutes(prefs.getInt("repeat_call_window_minutes", 5))
        set(value) = setCallPreference("repeat_call_window_minutes", RepeatCallPolicy.windowMinutes(value))

    var escalateCallVolume: Boolean
        get() = prefs.getBoolean("escalate_call_volume", false)
        set(value) = setCallPreference("escalate_call_volume", value)

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    var messageVolumePercent: Int
        get() = prefs.getInt(KEY_MESSAGE_VOLUME_PERCENT, DEFAULT_MESSAGE_VOLUME_PERCENT)
            .coerceIn(MIN_MESSAGE_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        set(value) = prefs.edit()
            .putInt(KEY_MESSAGE_VOLUME_PERCENT, value.coerceIn(MIN_MESSAGE_VOLUME_PERCENT, MAX_VOLUME_PERCENT))
            .apply()

    var messageSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSAGE_SOUND_ENABLED, DEFAULT_MESSAGE_SOUND_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGE_SOUND_ENABLED, value).apply()

    var messageSoundType: Int
        get() = prefs.getInt(KEY_MESSAGE_SOUND_TYPE, MESSAGE_SOUND_DEFAULT).let {
            if (it == MESSAGE_SOUND_CONTACT) MESSAGE_SOUND_CONTACT else MESSAGE_SOUND_DEFAULT
        }
        set(value) = prefs.edit()
            .putInt(KEY_MESSAGE_SOUND_TYPE, if (value == MESSAGE_SOUND_CONTACT) MESSAGE_SOUND_CONTACT else MESSAGE_SOUND_DEFAULT)
            .apply()

    var muteUntilTimestamp: Long
        get() = prefs.getLong(KEY_MUTE_UNTIL, 0L)
        set(value) = setCallPreference(KEY_MUTE_UNTIL, value)

    val isMuted: Boolean
        get() {
            val until = muteUntilTimestamp
            val muted = MutePolicy.isMuted(until, System.currentTimeMillis())
            if (!muted && until != 0L) {
                muteUntilTimestamp = 0L
            }
            return muted
        }

    var overrideSoundType: Int
        get() = prefs.getInt(KEY_OVERRIDE_SOUND_TYPE, SOUND_TYPE_RINGTONE)
        set(value) = prefs.edit().putInt(KEY_OVERRIDE_SOUND_TYPE, value).apply()

    fun getContacts(): List<VipContact> {
        val json = prefs.getString(KEY_CONTACTS, null) ?: return DEFAULT_CONTACTS
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                VipContact(
                    name = obj.getString("name"),
                    number = obj.getString("number"),
                    locationEnabled = obj.optBoolean("locationEnabled", false),
                    ringtoneEnabled = obj.optBoolean("ringtoneEnabled", true),
                    callAlertMode = CallAlertMode.parse(obj.optString("callAlertMode"), CallAlertMode.INHERIT)
                )
            }
        } catch (e: Exception) {
            DEFAULT_CONTACTS
        }
    }

    fun saveContacts(contacts: List<VipContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
                put("locationEnabled", c.locationEnabled)
                put("ringtoneEnabled", c.ringtoneEnabled)
                put("callAlertMode", c.callAlertMode.name)
            })
        }
        setCallPreference(KEY_CONTACTS, arr.toString())
    }

    fun getVipNumbers(): Set<String> {
        return getContacts().map { normalizeNumber(it.number) }.toSet()
    }

    fun findVipContact(incoming: String): VipContact? {
        if (PhoneUtils.normalize(incoming).isBlank()) return null
        return getContacts().find { contact -> PhoneUtils.matches(incoming, contact.number) }
    }

    fun normalizeNumber(number: String): String = PhoneUtils.normalize(number)

    fun getQuietRules(): List<QuietRule> {
        val json = prefs.getString(KEY_QUIET_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val daysArr = obj.getJSONArray("days")
                val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
                QuietRule(
                    days = days,
                    startHour = obj.getInt("startHour"),
                    startMinute = obj.getInt("startMinute"),
                    endHour = obj.getInt("endHour"),
                    endMinute = obj.getInt("endMinute")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveQuietRules(rules: List<QuietRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("days", JSONArray(r.days.toList()))
                put("startHour", r.startHour)
                put("startMinute", r.startMinute)
                put("endHour", r.endHour)
                put("endMinute", r.endMinute)
            })
        }
        setCallPreference(KEY_QUIET_RULES, arr.toString())
    }

    fun isInQuietPeriod(): Boolean {
        val rules = getQuietRules()
        if (rules.isEmpty()) return false

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return QuietHoursPolicy.isQuiet(rules, currentDay, currentTime)
    }

    var ownPhoneNumber: String
        get() = prefs.getString(KEY_OWN_NUMBER, "") ?: ""
        set(value) {
            val normalized = normalizeNumber(value)
            prefs.edit()
                .putString(KEY_OWN_NUMBER, normalized)
                .apply()
        }

    var ntfyServerUrl: String
        get() = prefs.getString(KEY_NTFY_SERVER_URL, DEFAULT_NTFY_SERVER) ?: DEFAULT_NTFY_SERVER
        set(value) = prefs.edit().putString(KEY_NTFY_SERVER_URL, value).apply()

    var ntfyAuthToken: String
        get() = SecretStore.unwrap(prefs.getString(KEY_NTFY_AUTH_TOKEN, null)) ?: DEFAULT_NTFY_AUTH_TOKEN
        set(value) = prefs.edit().putString(KEY_NTFY_AUTH_TOKEN, SecretStore.wrap(value)).apply()

    var userPassphrase: String?
        get() = SecretStore.unwrap(prefs.getString(KEY_USER_PASSPHRASE, null))
        set(value) = prefs.edit().putString(KEY_USER_PASSPHRASE, value?.let { SecretStore.wrap(it) }).apply()

    fun migrateSecretsIfNeeded() {
        val rawPassphrase = prefs.getString(KEY_USER_PASSPHRASE, null)
        if (rawPassphrase != null && !SecretStore.isWrapped(rawPassphrase)) {
            userPassphrase = rawPassphrase
        }
        val rawToken = prefs.getString(KEY_NTFY_AUTH_TOKEN, null)
        if (rawToken != null && !SecretStore.isWrapped(rawToken)) {
            ntfyAuthToken = rawToken
        }
    }

    var passphraseCreatedAt: Long
        get() = prefs.getLong(KEY_PASSPHRASE_CREATED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PASSPHRASE_CREATED_AT, value).apply()

    var lastPermWarningMs: Long
        get() = prefs.getLong(KEY_LAST_PERM_WARNING, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PERM_WARNING, value).apply()

    var overrideActive: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE_ACTIVE, value).apply()

    var savedAlarmVolume: Int
        get() = prefs.getInt(KEY_SAVED_ALARM_VOLUME, 0)
        set(value) = prefs.edit().putInt(KEY_SAVED_ALARM_VOLUME, value).apply()

    var savedDndFilter: Int
        get() = prefs.getInt(KEY_SAVED_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        set(value) = prefs.edit().putInt(KEY_SAVED_DND_FILTER, value).apply()

    var pendingUpdateToken: String?
        get() = prefs.getString(KEY_PENDING_UPDATE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_PENDING_UPDATE_TOKEN, value).apply()

    fun updateContactLocationEnabled(number: String, enabled: Boolean) {
        saveContacts(applyLocationEnabledUpdate(getContacts(), number, enabled))
    }

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
}
