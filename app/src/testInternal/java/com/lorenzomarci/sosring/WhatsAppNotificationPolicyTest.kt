package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppNotificationPolicyTest {

    @Test
    fun acceptsDirectWhatsAppMessageWithShortcut() {
        assertTrue(
            WhatsAppNotificationPolicy.shouldInspect(
                packageName = "com.whatsapp",
                isGroupSummary = false,
                category = "msg",
                shortcutId = "conversation-id",
                messageCount = 1
            )
        )
    }

    @Test
    fun rejectsOtherPackagesSummariesAndIncompleteRecords() {
        assertFalse(WhatsAppNotificationPolicy.shouldInspect("other.app", false, "msg", "id", 1))
        assertFalse(WhatsAppNotificationPolicy.shouldInspect("com.whatsapp", true, "msg", "id", 1))
        assertFalse(WhatsAppNotificationPolicy.shouldInspect("com.whatsapp", false, "call", "id", 1))
        assertFalse(WhatsAppNotificationPolicy.shouldInspect("com.whatsapp", false, "msg", null, 1))
        assertFalse(WhatsAppNotificationPolicy.shouldInspect("com.whatsapp", false, "msg", "id", 0))
    }

    @Test
    fun shortcutHashIsStableAndDoesNotExposeRawId() {
        val first = WhatsAppNotificationPolicy.shortcutHash("private-conversation-id")
        val second = WhatsAppNotificationPolicy.shortcutHash("private-conversation-id")
        assertEquals(first, second)
        assertFalse(first.contains("private-conversation-id"))
        assertEquals(64, first.length)
    }

    @Test
    fun eventFingerprintChangesForNewTimestampOrMessageCount() {
        val first = WhatsAppNotificationPolicy.eventFingerprint("id", 100L, 1)
        assertNotEquals(first, WhatsAppNotificationPolicy.eventFingerprint("id", 101L, 1))
        assertNotEquals(first, WhatsAppNotificationPolicy.eventFingerprint("id", 100L, 2))
    }
}
