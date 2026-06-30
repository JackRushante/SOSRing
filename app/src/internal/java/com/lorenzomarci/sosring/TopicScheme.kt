package com.lorenzomarci.sosring

import java.security.MessageDigest

object TopicScheme {
    fun computeTopicHash(normalizedNumber: String): String {
        if (normalizedNumber.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalizedNumber.toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        return "sosring-${hex.take(16)}"
    }

    fun own(prefs: PrefsManager): String = computeTopicHash(prefs.ownPhoneNumber)

    fun forNumber(prefs: PrefsManager, number: String): String =
        computeTopicHash(prefs.normalizeNumber(number))
}
