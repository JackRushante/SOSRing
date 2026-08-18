package com.lorenzomarci.sosring

import java.security.MessageDigest

object VipMessageNotificationPolicy {
    private val packages = mapOf(
        "com.whatsapp" to MessageApp.WHATSAPP,
        "com.google.android.apps.messaging" to MessageApp.GOOGLE_MESSAGES,
        "org.telegram.messenger" to MessageApp.TELEGRAM,
        "org.telegram.messenger.web" to MessageApp.TELEGRAM
    )

    fun appForPackage(packageName: String): MessageApp? = packages[packageName]

    fun shouldInspect(
        app: MessageApp,
        isGroupSummary: Boolean,
        isGroupConversation: Boolean,
        category: String?,
        conversationId: String?,
        messageCount: Int
    ): Boolean = !isGroupSummary &&
        !isGroupConversation &&
        (category == null || category == "msg") &&
        !conversationId.isNullOrBlank() &&
        messageCount > 0

    fun conversationHash(conversationId: String): String = sha256(conversationId)

    fun eventFingerprint(
        conversationId: String,
        latestMessageTime: Long,
        messageCount: Int
    ): String = sha256("$conversationId|$latestMessageTime|$messageCount")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
