package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VipMessageNotificationPolicyTest {

    @Test
    fun mapsSupportedPackages() {
        assertEquals(MessageApp.WHATSAPP, VipMessageNotificationPolicy.appForPackage("com.whatsapp"))
        assertEquals(
            MessageApp.SMS,
            VipMessageNotificationPolicy.appForPackage("com.google.android.apps.messaging")
        )
        assertEquals(MessageApp.TELEGRAM, VipMessageNotificationPolicy.appForPackage("org.telegram.messenger"))
        assertEquals(MessageApp.TELEGRAM, VipMessageNotificationPolicy.appForPackage("org.telegram.messenger.web"))
        assertNull(VipMessageNotificationPolicy.appForPackage("other.app"))
    }

    @Test
    fun mapsFossSmsAppsAndTheDefaultSmsApp() {
        assertEquals(MessageApp.SMS, VipMessageNotificationPolicy.appForPackage("foundation.e.message"))
        assertEquals(MessageApp.SMS, VipMessageNotificationPolicy.appForPackage("org.fossify.messages"))
        assertEquals(MessageApp.SMS, VipMessageNotificationPolicy.appForPackage("com.android.messaging"))
        assertEquals(
            MessageApp.SMS,
            VipMessageNotificationPolicy.appForPackage("some.oem.sms", defaultSmsPackage = "some.oem.sms")
        )
        assertNull(VipMessageNotificationPolicy.appForPackage("some.oem.sms", defaultSmsPackage = "other"))
        assertNull(VipMessageNotificationPolicy.appForPackage("some.oem.sms", defaultSmsPackage = null))
        assertEquals(
            MessageApp.WHATSAPP,
            VipMessageNotificationPolicy.appForPackage("com.whatsapp", defaultSmsPackage = "com.whatsapp")
        )
    }

    @Test
    fun smsConversationIdFallsBackToTheNotificationId() {
        assertEquals(
            "foundation.e.message|id:42",
            VipMessageNotificationPolicy.conversationId(MessageApp.SMS, "foundation.e.message", null, null, 42)
        )
        assertEquals(
            "org.fossify.messages|7",
            VipMessageNotificationPolicy.conversationId(MessageApp.SMS, "org.fossify.messages", "7", null, 1)
        )
        assertEquals(
            "org.fossify.messages|thread",
            VipMessageNotificationPolicy.conversationId(MessageApp.SMS, "org.fossify.messages", " ", "thread", 1)
        )
    }

    @Test
    fun googleMessagesKeepsTheBareShortcutIdOfExistingPairings() {
        assertEquals(
            "shortcut",
            VipMessageNotificationPolicy.conversationId(
                MessageApp.SMS, VipMessageNotificationPolicy.GOOGLE_MESSAGES_PACKAGE, "shortcut", null, 1
            )
        )
    }

    @Test
    fun chatAppsStillNeedAShortcutOrTag() {
        assertNull(VipMessageNotificationPolicy.conversationId(MessageApp.WHATSAPP, "com.whatsapp", null, null, 5))
        assertEquals(
            "jid",
            VipMessageNotificationPolicy.conversationId(MessageApp.WHATSAPP, "com.whatsapp", "jid", null, 5)
        )
    }

    @Test
    fun smsWithoutMessagingStyleIsInspectedButOngoingIsNot() {
        assertTrue(shouldInspect(app = MessageApp.SMS, messageCount = 0))
        assertFalse(shouldInspect(app = MessageApp.SMS, isOngoing = true))
        assertFalse(shouldInspect(app = MessageApp.SMS, isGroupConversation = true))
    }

    @Test
    fun recognisesPhoneNumbersButNotNames() {
        assertEquals("+39 333 111 2222", VipMessageNotificationPolicy.phoneNumberIn("+39 333 111 2222"))
        assertEquals("3331112222", VipMessageNotificationPolicy.phoneNumberIn("tel:3331112222"))
        assertEquals("(02) 1234-567", VipMessageNotificationPolicy.phoneNumberIn("(02) 1234-567"))
        assertNull(VipMessageNotificationPolicy.phoneNumberIn("Mamma"))
        assertNull(VipMessageNotificationPolicy.phoneNumberIn("Mario 333111222"))
        assertNull(VipMessageNotificationPolicy.phoneNumberIn("12345"))
        assertNull(VipMessageNotificationPolicy.phoneNumberIn(null))
        assertNull(VipMessageNotificationPolicy.phoneNumberIn(""))
    }

    @Test
    fun acceptsDirectMessageWithConversationId() {
        assertTrue(shouldInspect(category = "msg"))
        assertTrue(shouldInspect(category = null))
    }

    @Test
    fun rejectsSummariesGroupsCallsAndIncompleteRecords() {
        assertFalse(shouldInspect(isGroupSummary = true))
        assertFalse(shouldInspect(isGroupConversation = true))
        assertFalse(shouldInspect(category = "call"))
        assertFalse(shouldInspect(conversationId = null))
        assertFalse(shouldInspect(messageCount = 0))
    }

    @Test
    fun conversationHashIsStableAndPrivate() {
        val first = VipMessageNotificationPolicy.conversationHash("private-id")
        val second = VipMessageNotificationPolicy.conversationHash("private-id")
        assertEquals(first, second)
        assertFalse(first.contains("private-id"))
        assertEquals(64, first.length)
    }

    @Test
    fun eventFingerprintChangesForTimestampOrMessageCount() {
        val first = VipMessageNotificationPolicy.eventFingerprint("id", 100L, 1)
        assertNotEquals(first, VipMessageNotificationPolicy.eventFingerprint("id", 101L, 1))
        assertNotEquals(first, VipMessageNotificationPolicy.eventFingerprint("id", 100L, 2))
    }

    private fun shouldInspect(
        app: MessageApp = MessageApp.WHATSAPP,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        isGroupConversation: Boolean = false,
        category: String? = "msg",
        conversationId: String? = "conversation-id",
        messageCount: Int = 1
    ) = VipMessageNotificationPolicy.shouldInspect(
        app = app,
        isGroupSummary = isGroupSummary,
        isGroupConversation = isGroupConversation,
        category = category,
        conversationId = conversationId,
        messageCount = messageCount,
        isOngoing = isOngoing
    )
}
