package com.lorenzomarci.sosring

import android.content.Context
import org.json.JSONObject

class WhatsAppBindingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pendingNumber: String?
        get() = prefs.getString(KEY_PENDING_NUMBER, null)
        set(value) {
            val normalized = value?.let(PhoneUtils::normalize)?.takeIf { it.isNotBlank() }
            prefs.edit().apply {
                if (normalized == null) remove(KEY_PENDING_NUMBER) else putString(KEY_PENDING_NUMBER, normalized)
            }.apply()
        }

    fun state(number: String): WhatsAppAlertState {
        val normalized = PhoneUtils.normalize(number)
        return when {
            pendingNumber == normalized -> WhatsAppAlertState.PAIRING
            bindingFor(normalized) != null -> WhatsAppAlertState.PAIRED
            else -> WhatsAppAlertState.UNPAIRED
        }
    }

    fun bind(number: String, shortcutHash: String, initialEventFingerprint: String) {
        val normalized = PhoneUtils.normalize(number)
        if (normalized.isBlank()) return
        val bindings = readObject(KEY_BINDINGS)
        bindings.put(normalized, shortcutHash)
        val events = readObject(KEY_LAST_EVENTS)
        events.put(shortcutHash, initialEventFingerprint)
        prefs.edit()
            .putString(KEY_BINDINGS, bindings.toString())
            .putString(KEY_LAST_EVENTS, events.toString())
            .remove(KEY_PENDING_NUMBER)
            .apply()
    }

    fun contactForShortcut(shortcutHash: String, contacts: List<VipContact>): VipContact? {
        val bindings = readObject(KEY_BINDINGS)
        return contacts.firstOrNull { contact ->
            bindings.optString(PhoneUtils.normalize(contact.number), "") == shortcutHash
        }
    }

    fun isNewEvent(shortcutHash: String, fingerprint: String): Boolean {
        val events = readObject(KEY_LAST_EVENTS)
        if (events.optString(shortcutHash, "") == fingerprint) return false
        events.put(shortcutHash, fingerprint)
        prefs.edit().putString(KEY_LAST_EVENTS, events.toString()).apply()
        return true
    }

    fun unpair(number: String) {
        val normalized = PhoneUtils.normalize(number)
        val bindings = readObject(KEY_BINDINGS)
        val oldHash = bindings.optString(normalized, "")
        bindings.remove(normalized)
        val events = readObject(KEY_LAST_EVENTS)
        if (oldHash.isNotBlank() && !hasHashBinding(bindings, oldHash)) events.remove(oldHash)
        prefs.edit()
            .putString(KEY_BINDINGS, bindings.toString())
            .putString(KEY_LAST_EVENTS, events.toString())
            .apply()
        if (pendingNumber == normalized) pendingNumber = null
    }

    fun migrateNumber(oldNumber: String, newNumber: String) {
        val oldNormalized = PhoneUtils.normalize(oldNumber)
        val newNormalized = PhoneUtils.normalize(newNumber)
        if (oldNormalized.isBlank() || newNormalized.isBlank() || oldNormalized == newNormalized) return
        val bindings = readObject(KEY_BINDINGS)
        if (bindings.has(oldNormalized)) {
            bindings.put(newNormalized, bindings.getString(oldNormalized))
            bindings.remove(oldNormalized)
            prefs.edit().putString(KEY_BINDINGS, bindings.toString()).apply()
        }
        if (pendingNumber == oldNormalized) pendingNumber = newNormalized
    }

    private fun bindingFor(normalizedNumber: String): String? =
        readObject(KEY_BINDINGS).optString(normalizedNumber, "").takeIf { it.isNotBlank() }

    private fun readObject(key: String): JSONObject = try {
        JSONObject(prefs.getString(key, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun hasHashBinding(bindings: JSONObject, shortcutHash: String): Boolean {
        val keys = bindings.keys()
        while (keys.hasNext()) {
            if (bindings.optString(keys.next(), "") == shortcutHash) return true
        }
        return false
    }

    companion object {
        private const val PREFS_NAME = "whatsapp_vip_alerts"
        private const val KEY_PENDING_NUMBER = "pending_number"
        private const val KEY_BINDINGS = "bindings"
        private const val KEY_LAST_EVENTS = "last_events"
    }
}
