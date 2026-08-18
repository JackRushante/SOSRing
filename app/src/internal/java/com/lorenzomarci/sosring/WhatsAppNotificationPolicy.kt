package com.lorenzomarci.sosring

import java.security.MessageDigest

object WhatsAppNotificationPolicy {
    const val PACKAGE_WHATSAPP = "com.whatsapp"

    fun shouldInspect(
        packageName: String,
        isGroupSummary: Boolean,
        category: String?,
        shortcutId: String?,
        messageCount: Int
    ): Boolean = packageName == PACKAGE_WHATSAPP &&
        !isGroupSummary &&
        (category == null || category == "msg") &&
        !shortcutId.isNullOrBlank() &&
        messageCount > 0

    fun shortcutHash(shortcutId: String): String = sha256(shortcutId)

    fun eventFingerprint(shortcutId: String, latestMessageTime: Long, messageCount: Int): String =
        sha256("$shortcutId|$latestMessageTime|$messageCount")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
