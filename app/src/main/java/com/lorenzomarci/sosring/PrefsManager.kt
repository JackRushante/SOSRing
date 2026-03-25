package com.lorenzomarci.sosring

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class VipContact(val name: String, val number: String)

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

        val DEFAULT_CONTACTS = listOf(
            VipContact("Lorenzo", "+39393207780"),
            VipContact("Padre", "+393358027893"),
            VipContact("Madre", "+393515713262")
        )
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
                VipContact(obj.getString("name"), obj.getString("number"))
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
            })
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    fun getVipNumbers(): Set<String> {
        return getContacts().map { normalizeNumber(it.number) }.toSet()
    }

    fun normalizeNumber(number: String): String {
        return number.replace(Regex("[\\s\\-().]"), "")
    }
}
