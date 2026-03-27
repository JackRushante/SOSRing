package com.lorenzomarci.sosring

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

data class VipContact(val name: String, val number: String, val locationEnabled: Boolean = false)

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

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sosring_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONTACTS = "vip_contacts"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VOLUME_PERCENT = "volume_percent"
        const val MIN_VOLUME_PERCENT = 50
        const val MAX_VOLUME_PERCENT = 100
        const val DEFAULT_VOLUME_PERCENT = 100

        val DEFAULT_CONTACTS = emptyList<VipContact>()

        private const val KEY_QUIET_RULES = "quiet_rules"
        const val MAX_QUIET_RULES = 10

        private const val KEY_OWN_NUMBER = "own_phone_number"
        private const val KEY_OWN_TOPIC_HASH = "own_topic_hash"
        private const val KEY_NTFY_SERVER_URL = "ntfy_server_url"
        val DEFAULT_NTFY_SERVER = BuildConfig.NTFY_SERVER

        private const val KEY_LOCATION_LOGS = "location_logs"
        private const val LOG_RETENTION_DAYS = 30
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var volumePercent: Int
        get() = prefs.getInt(KEY_VOLUME_PERCENT, DEFAULT_VOLUME_PERCENT)
        set(value) = prefs.edit().putInt(KEY_VOLUME_PERCENT, value.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)).apply()

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

    fun getVipNumbers(): Set<String> {
        return getContacts().map { normalizeNumber(it.number) }.toSet()
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
        prefs.edit().putString(KEY_QUIET_RULES, arr.toString()).apply()
    }

    fun isInQuietPeriod(): Boolean {
        val rules = getQuietRules()
        if (rules.isEmpty()) return false

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY
            else if (currentDay == Calendar.MONDAY) Calendar.SUNDAY
            else currentDay - 1

        return rules.any { rule ->
            val start = rule.startHour * 60 + rule.startMinute
            val end = rule.endHour * 60 + rule.endMinute

            if (end > start) {
                // Same-day rule: e.g. 09:00-18:00
                currentDay in rule.days && currentTime >= start && currentTime < end
            } else {
                // Cross-midnight rule: e.g. 22:00-06:00
                (currentDay in rule.days && currentTime >= start) ||
                (previousDay in rule.days && currentTime < end)
            }
        }
    }

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
