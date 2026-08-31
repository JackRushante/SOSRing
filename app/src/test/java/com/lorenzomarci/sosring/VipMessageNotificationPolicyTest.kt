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
            MessageApp.GOOGLE_MESSAGES,
            VipMessageNotificationPolicy.appForPackage("com.google.android.apps.messaging")
        )
        assertEquals(MessageApp.TELEGRAM, VipMessageNotificationPolicy.appForPackage("org.telegram.messenger"))
        assertEquals(MessageApp.TELEGRAM, VipMessageNotificationPolicy.appForPackage("org.telegram.messenger.web"))
        assertNull(VipMessageNotificationPolicy.appForPackage("other.app"))
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
        isGroupSummary: Boolean = false,
        isGroupConversation: Boolean = false,
        category: String? = "msg",
        conversationId: String? = "conversation-id",
        messageCount: Int = 1
    ) = VipMessageNotificationPolicy.shouldInspect(
        app = MessageApp.WHATSAPP,
        isGroupSummary = isGroupSummary,
        isGroupConversation = isGroupConversation,
        category = category,
        conversationId = conversationId,
        messageCount = messageCount
    )
}
