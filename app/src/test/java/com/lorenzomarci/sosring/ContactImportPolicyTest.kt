package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactImportPolicyTest {
    @Test fun uniqueOptionsDropsFormattingDuplicatesAndInvalidNumbers() {
        val result = ContactImportPolicy.uniqueOptions(
            listOf(
                ContactPhoneOption("+39 333 123 4567", "Mobile"),
                ContactPhoneOption("+393331234567", "Private"),
                ContactPhoneOption("12", "Invalid")
            )
        )
        assertEquals(listOf(ContactPhoneOption("+39 333 123 4567", "Mobile")), result)
    }

    @Test fun multipleNumbersBecomeSeparateLabeledVipContacts() {
        val result = ContactImportPolicy.createVipContacts(
            contactName = "Fire alarm",
            selected = listOf(
                ContactPhoneOption("+393331234567", "Primary"),
                ContactPhoneOption("+393339999999", "Backup")
            ),
            existing = emptyList(),
            includeLabels = true
        )
        assertEquals("Fire alarm (Primary)", result[0].name)
        assertEquals("Fire alarm (Backup)", result[1].name)
    }

    @Test fun alreadyConfiguredNumberIsSkipped() {
        val result = ContactImportPolicy.createVipContacts(
            contactName = "Fire alarm",
            selected = listOf(ContactPhoneOption("333 123 4567", "Mobile")),
            existing = listOf(VipContact("Existing", "+39 333 123 4567")),
            includeLabels = true
        )
        assertTrue(result.isEmpty())
    }

    @Test fun singleNumberKeepsPlainContactName() {
        assertEquals(
            "Clizia",
            ContactImportPolicy.displayName(
                "Clizia",
                ContactPhoneOption("+393331234567", "Mobile"),
                includeLabel = false
            )
        )
    }
}
