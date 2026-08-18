package com.lorenzomarci.sosring

import org.json.JSONArray
import org.json.JSONObject

data class AppConfig(
    val version: Int,
    val exportedAt: Long,
    val passphrase: String?,
    val passphraseCreatedAt: Long,
    val ownPhoneNumber: String,
    val ntfyServerUrl: String,
    val ntfyAuthToken: String,
    val volumePercent: Int,
    val overrideSoundType: Int,
    val messageVolumePercent: Int,
    val messageSoundType: Int,
    val contacts: List<VipContact>,
    val quietRules: List<QuietRule>
)

object ConfigExporter {

    private const val CURRENT_VERSION = 1

    fun isValidRuleRanges(
        startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, days: Set<Int>
    ): Boolean {
        return startHour in 0..23 && endHour in 0..23 &&
            startMinute in 0..59 && endMinute in 0..59 &&
            days.isNotEmpty() && days.all { it in 1..7 } &&
            QuietRuleValidator.isValid(days, startHour, startMinute, endHour, endMinute)
    }

    fun buildConfig(
        passphrase: String?,
        passphraseCreatedAt: Long,
        ownPhoneNumber: String,
        ntfyServerUrl: String,
        ntfyAuthToken: String,
        volumePercent: Int,
        overrideSoundType: Int,
        messageVolumePercent: Int,
        messageSoundType: Int,
        contacts: List<VipContact>,
        quietRules: List<QuietRule>
    ): AppConfig = AppConfig(
        version = CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        passphrase = passphrase,
        passphraseCreatedAt = passphraseCreatedAt,
        ownPhoneNumber = ownPhoneNumber,
        ntfyServerUrl = ntfyServerUrl,
        ntfyAuthToken = ntfyAuthToken,
        volumePercent = volumePercent,
        overrideSoundType = overrideSoundType,
        messageVolumePercent = messageVolumePercent,
        messageSoundType = messageSoundType,
        contacts = contacts,
        quietRules = quietRules
    )

    fun export(config: AppConfig): String {
        val contactsArr = JSONArray()
        config.contacts.forEach { c ->
            contactsArr.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
                put("locationEnabled", c.locationEnabled)
                put("ringtoneEnabled", c.ringtoneEnabled)
            })
        }
        val rulesArr = JSONArray()
        config.quietRules.forEach { r ->
            rulesArr.put(JSONObject().apply {
                put("days", JSONArray(r.days.toList()))
                put("startHour", r.startHour)
                put("startMinute", r.startMinute)
                put("endHour", r.endHour)
                put("endMinute", r.endMinute)
            })
        }
        val root = JSONObject().apply {
            put("version", config.version)
            put("exportedAt", config.exportedAt)
            put("passphrase", config.passphrase ?: JSONObject.NULL)
            put("passphraseCreatedAt", config.passphraseCreatedAt)
            put("ownPhoneNumber", config.ownPhoneNumber)
            put("ntfyServerUrl", config.ntfyServerUrl)
            put("ntfyAuthToken", config.ntfyAuthToken)
            put("volumePercent", config.volumePercent)
            put("overrideSoundType", config.overrideSoundType)
            put("messageVolumePercent", config.messageVolumePercent)
            put("messageSoundType", config.messageSoundType)
            put("contacts", contactsArr)
            put("quietRules", rulesArr)
        }
        return root.toString(2)
    }

    fun import(json: String): AppConfig? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", CURRENT_VERSION)
            val exportedAt = root.optLong("exportedAt", 0L)
            val passphrase = if (root.has("passphrase") && !root.isNull("passphrase")) {
                root.getString("passphrase")
            } else {
                null
            }
            val passphraseCreatedAt = root.optLong("passphraseCreatedAt", 0L)
            val ownPhoneNumber = root.optString("ownPhoneNumber", "")
            val ntfyServerUrl = root.optString("ntfyServerUrl", "")
            val ntfyAuthToken = root.optString("ntfyAuthToken", "")
            val volumePercent = root.optInt("volumePercent", PrefsManager.DEFAULT_VOLUME_PERCENT)
            val overrideSoundType = root.optInt("overrideSoundType", PrefsManager.SOUND_TYPE_RINGTONE)
            val messageVolumePercent = root.optInt(
                "messageVolumePercent",
                PrefsManager.DEFAULT_MESSAGE_VOLUME_PERCENT
            )
            val messageSoundType = root.optInt("messageSoundType", PrefsManager.MESSAGE_SOUND_DEFAULT)

            val contactsArr = root.optJSONArray("contacts")
            val contacts = mutableListOf<VipContact>()
            if (contactsArr != null) {
                for (i in 0 until contactsArr.length()) {
                    val obj = contactsArr.getJSONObject(i)
                    contacts.add(VipContact(
                        name = obj.optString("name", ""),
                        number = obj.optString("number", ""),
                        locationEnabled = obj.optBoolean("locationEnabled", false),
                        ringtoneEnabled = obj.optBoolean("ringtoneEnabled", true)
                    ))
                }
            }

            val rulesArr = root.optJSONArray("quietRules")
            val rules = mutableListOf<QuietRule>()
            if (rulesArr != null) {
                for (i in 0 until rulesArr.length()) {
                    val obj = rulesArr.getJSONObject(i)
                    val daysArr = obj.optJSONArray("days")
                    val days = mutableSetOf<Int>()
                    if (daysArr != null) {
                        for (j in 0 until daysArr.length()) {
                            days.add(daysArr.getInt(j))
                        }
                    }
                    val sh = obj.optInt("startHour", 0)
                    val sm = obj.optInt("startMinute", 0)
                    val eh = obj.optInt("endHour", 0)
                    val em = obj.optInt("endMinute", 0)
                    if (isValidRuleRanges(sh, sm, eh, em, days)) {
                        rules.add(QuietRule(
                            days = days,
                            startHour = sh,
                            startMinute = sm,
                            endHour = eh,
                            endMinute = em
                        ))
                    }
                }
            }

            AppConfig(
                version = version,
                exportedAt = exportedAt,
                passphrase = passphrase,
                passphraseCreatedAt = passphraseCreatedAt,
                ownPhoneNumber = ownPhoneNumber,
                ntfyServerUrl = ntfyServerUrl,
                ntfyAuthToken = ntfyAuthToken,
                volumePercent = volumePercent,
                overrideSoundType = overrideSoundType,
                messageVolumePercent = messageVolumePercent,
                messageSoundType = messageSoundType,
                contacts = contacts,
                quietRules = rules
            )
        } catch (e: Exception) {
            null
        }
    }
}
