package com.lorenzomarci.sosring

import java.security.MessageDigest

object VipMessageNotificationPolicy {
    const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"

    private val packages = mapOf(
        "com.whatsapp" to MessageApp.WHATSAPP,
        "org.telegram.messenger" to MessageApp.TELEGRAM,
        "org.telegram.messenger.web" to MessageApp.TELEGRAM
    )

    /**
     * Well-known SMS apps. The device's default SMS app is accepted too, whatever its package,
     * so ROMs without Google Messages (/e/OS, LineageOS, OEM builds) work out of the box.
     */
    private val smsPackages = setOf(
        GOOGLE_MESSAGES_PACKAGE,
        "foundation.e.message",
        "com.moez.QKSMS",
        "org.fossify.messages",
        "com.simplemobiletools.smsmessenger",
        "com.android.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.oneplus.mms"
    )

    fun appForPackage(packageName: String, defaultSmsPackage: String? = null): MessageApp? {
        packages[packageName]?.let { return it }
        if (packageName in smsPackages) return MessageApp.SMS
        if (!defaultSmsPackage.isNullOrBlank() && packageName == defaultSmsPackage) return MessageApp.SMS
        return null
    }

    /**
     * The identifier that tells one conversation from another. Chat apps expose a shortcut id or
     * a tag; many SMS apps have neither and post one notification per thread under a stable id.
     * SMS ids other than Google Messages' carry the package, so two SMS apps never collide; Google
     * Messages keeps the bare id so its existing pairings stay valid.
     */
    fun conversationId(
        app: MessageApp,
        packageName: String,
        shortcutId: String?,
        tag: String?,
        notificationId: Int
    ): String? {
        val base = shortcutId?.takeIf { it.isNotBlank() }
            ?: tag?.takeIf { it.isNotBlank() }
        if (app != MessageApp.SMS) return base
        val id = base ?: "id:$notificationId"
        return if (packageName == GOOGLE_MESSAGES_PACKAGE && base != null) id else "$packageName|$id"
    }

    fun shouldInspect(
        app: MessageApp,
        isGroupSummary: Boolean,
        isGroupConversation: Boolean,
        category: String?,
        conversationId: String?,
        messageCount: Int,
        isOngoing: Boolean = false
    ): Boolean = !isGroupSummary &&
        !isGroupConversation &&
        !isOngoing &&
        (category == null || category == "msg") &&
        !conversationId.isNullOrBlank() &&
        // SMS apps may hide the text (preview off) and post a plain notification without messages.
        (messageCount > 0 || app == MessageApp.SMS)

    /** The phone number a notification field holds, or null when it is a name or other text. */
    fun phoneNumberIn(value: CharSequence?): String? {
        val text = value?.toString()?.trim()?.removePrefix("tel:") ?: return null
        if (text.isEmpty() || text.any { it !in PHONE_CHARS && !it.isDigit() }) return null
        val digits = text.count { it.isDigit() }
        return if (digits in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) text else null
    }

    fun conversationHash(conversationId: String): String = sha256(conversationId)

    fun eventFingerprint(
        conversationId: String,
        latestMessageTime: Long,
        messageCount: Int
    ): String = sha256("$conversationId|$latestMessageTime|$messageCount")

    private const val PHONE_CHARS = "+ -(). "
    private const val MIN_PHONE_DIGITS = 6
    private const val MAX_PHONE_DIGITS = 15

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
