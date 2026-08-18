package com.lorenzomarci.sosring

import android.content.Context
import org.json.JSONObject

data class PendingMessagePairing(val number: String, val app: MessageApp)

class VipMessageBindingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyWhatsAppBindings()
    }

    val pending: PendingMessagePairing?
        get() {
            val number = prefs.getString(KEY_PENDING_NUMBER, null)?.takeIf { it.isNotBlank() } ?: return null
            val app = MessageApp.fromStorageId(prefs.getString(KEY_PENDING_APP, null)) ?: return null
            return PendingMessagePairing(number, app)
        }

    fun beginPairing(number: String, app: MessageApp) {
        val normalized = PhoneUtils.normalize(number)
        if (normalized.isBlank()) return
        prefs.edit()
            .putString(KEY_PENDING_NUMBER, normalized)
            .putString(KEY_PENDING_APP, app.storageId)
            .apply()
    }

    fun cancelPairing() {
        prefs.edit().remove(KEY_PENDING_NUMBER).remove(KEY_PENDING_APP).apply()
    }

    fun state(number: String, app: MessageApp): MessageAlertState {
        val normalized = PhoneUtils.normalize(number)
        return when {
            pending == PendingMessagePairing(normalized, app) -> MessageAlertState.PAIRING
            bindingFor(normalized, app) != null -> MessageAlertState.PAIRED
            else -> MessageAlertState.UNPAIRED
        }
    }

    fun bind(number: String, app: MessageApp, conversationHash: String, initialEventFingerprint: String) {
        val normalized = PhoneUtils.normalize(number)
        if (normalized.isBlank()) return
        val bindings = readObject(KEY_BINDINGS)
        bindings.put(bindingKey(app, normalized), conversationHash)
        val events = readObject(KEY_LAST_EVENTS)
        events.put(eventKey(app, conversationHash), initialEventFingerprint)
        prefs.edit()
            .putString(KEY_BINDINGS, bindings.toString())
            .putString(KEY_LAST_EVENTS, events.toString())
            .remove(KEY_PENDING_NUMBER)
            .remove(KEY_PENDING_APP)
            .apply()
    }

    fun contactForConversation(
        app: MessageApp,
        conversationHash: String,
        contacts: List<VipContact>
    ): VipContact? {
        val bindings = readObject(KEY_BINDINGS)
        return contacts.firstOrNull { contact ->
            bindings.optString(bindingKey(app, PhoneUtils.normalize(contact.number)), "") == conversationHash
        }
    }

    fun isNewEvent(app: MessageApp, conversationHash: String, fingerprint: String): Boolean {
        val key = eventKey(app, conversationHash)
        val events = readObject(KEY_LAST_EVENTS)
        if (events.optString(key, "") == fingerprint) return false
        events.put(key, fingerprint)
        prefs.edit().putString(KEY_LAST_EVENTS, events.toString()).apply()
        return true
    }

    fun unpair(number: String, app: MessageApp) {
        val normalized = PhoneUtils.normalize(number)
        val bindings = readObject(KEY_BINDINGS)
        val key = bindingKey(app, normalized)
        val oldHash = bindings.optString(key, "")
        bindings.remove(key)
        val events = readObject(KEY_LAST_EVENTS)
        if (oldHash.isNotBlank() && !hasConversationBinding(bindings, app, oldHash)) {
            events.remove(eventKey(app, oldHash))
        }
        prefs.edit()
            .putString(KEY_BINDINGS, bindings.toString())
            .putString(KEY_LAST_EVENTS, events.toString())
            .apply()
        if (pending == PendingMessagePairing(normalized, app)) cancelPairing()
    }

    fun unpairAll(number: String) {
        MessageApp.entries.forEach { unpair(number, it) }
        if (pending?.number == PhoneUtils.normalize(number)) cancelPairing()
    }

    fun migrateNumber(oldNumber: String, newNumber: String) {
        val oldNormalized = PhoneUtils.normalize(oldNumber)
        val newNormalized = PhoneUtils.normalize(newNumber)
        if (oldNormalized.isBlank() || newNormalized.isBlank() || oldNormalized == newNormalized) return
        val bindings = readObject(KEY_BINDINGS)
        var changed = false
        MessageApp.entries.forEach { app ->
            val oldKey = bindingKey(app, oldNormalized)
            if (bindings.has(oldKey)) {
                bindings.put(bindingKey(app, newNormalized), bindings.getString(oldKey))
                bindings.remove(oldKey)
                changed = true
            }
        }
        if (changed) prefs.edit().putString(KEY_BINDINGS, bindings.toString()).apply()
        pending?.takeIf { it.number == oldNormalized }?.let { beginPairing(newNormalized, it.app) }
    }

    private fun bindingFor(normalizedNumber: String, app: MessageApp): String? =
        readObject(KEY_BINDINGS).optString(bindingKey(app, normalizedNumber), "").takeIf { it.isNotBlank() }

    private fun migrateLegacyWhatsAppBindings() {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val bindings = readObject(KEY_BINDINGS)
        val events = readObject(KEY_LAST_EVENTS)
        val legacyBindings = readLegacyObject(KEY_BINDINGS)
        val legacyEvents = readLegacyObject(KEY_LAST_EVENTS)
        val keys = legacyBindings.keys()
        while (keys.hasNext()) {
            val number = keys.next()
            val hash = legacyBindings.optString(number, "")
            if (hash.isNotBlank()) {
                bindings.put(bindingKey(MessageApp.WHATSAPP, PhoneUtils.normalize(number)), hash)
                val fingerprint = legacyEvents.optString(hash, "")
                if (fingerprint.isNotBlank()) events.put(eventKey(MessageApp.WHATSAPP, hash), fingerprint)
            }
        }
        val legacyPending = legacyPrefs.getString(KEY_PENDING_NUMBER, null)
            ?.let(PhoneUtils::normalize)
            ?.takeIf { it.isNotBlank() }
        prefs.edit()
            .putString(KEY_BINDINGS, bindings.toString())
            .putString(KEY_LAST_EVENTS, events.toString())
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .apply {
                if (legacyPending != null) {
                    putString(KEY_PENDING_NUMBER, legacyPending)
                    putString(KEY_PENDING_APP, MessageApp.WHATSAPP.storageId)
                }
            }
            .apply()
    }

    private fun readObject(key: String): JSONObject = readJsonObject(prefs.getString(key, "{}"))
    private fun readLegacyObject(key: String): JSONObject = readJsonObject(legacyPrefs.getString(key, "{}"))

    private fun readJsonObject(value: String?): JSONObject = try {
        JSONObject(value ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun hasConversationBinding(bindings: JSONObject, app: MessageApp, conversationHash: String): Boolean {
        val prefix = "${app.storageId}|"
        val keys = bindings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith(prefix) && bindings.optString(key, "") == conversationHash) return true
        }
        return false
    }

    private fun bindingKey(app: MessageApp, normalizedNumber: String) = "${app.storageId}|$normalizedNumber"
    private fun eventKey(app: MessageApp, conversationHash: String) = "${app.storageId}|$conversationHash"

    companion object {
        private const val PREFS_NAME = "vip_message_alerts"
        private const val LEGACY_PREFS_NAME = "whatsapp_vip_alerts"
        private const val KEY_PENDING_NUMBER = "pending_number"
        private const val KEY_PENDING_APP = "pending_app"
        private const val KEY_BINDINGS = "bindings"
        private const val KEY_LAST_EVENTS = "last_events"
        private const val KEY_LEGACY_MIGRATED = "legacy_whatsapp_migrated"
    }
}
