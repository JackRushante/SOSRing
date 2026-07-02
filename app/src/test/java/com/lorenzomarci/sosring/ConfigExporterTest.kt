package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ConfigExporterTest {

    private fun sampleConfig(): AppConfig = ConfigExporter.buildConfig(
        passphrase = "dGVzdC1wYXNzcGhyYXNlLWZvci10ZXN0aW5nLW9ubHk=",
        passphraseCreatedAt = 1784000000000L,
        ownPhoneNumber = "+393932077480",
        ntfyServerUrl = "https://ntfy.example.com",
        ntfyAuthToken = "tk_test123",
        volumePercent = 75,
        overrideSoundType = 1,
        contacts = listOf(
            VipContact(name = "Clizia", number = "+393515713262", locationEnabled = true, ringtoneEnabled = true),
            VipContact(name = "Mamma", number = "+393515713260", locationEnabled = false, ringtoneEnabled = false)
        ),
        quietRules = listOf(
            QuietRule(days = setOf(Calendar.MONDAY, Calendar.TUESDAY), startHour = 22, startMinute = 0, endHour = 6, endMinute = 0)
        )
    )

    @Test
    fun exportImportRoundtrip() {
        val original = sampleConfig()
        val json = ConfigExporter.export(original)
        val restored = ConfigExporter.import(json)
        assertNotNull(restored)
        assertEquals(original.copy(exportedAt = restored!!.exportedAt), restored)
    }

    @Test
    fun exportProducesValidJson() {
        val config = sampleConfig()
        val json = ConfigExporter.export(config)
        val parsed = org.json.JSONObject(json)
        assertEquals(1, parsed.getInt("version"))
        assertEquals("+393932077480", parsed.getString("ownPhoneNumber"))
        assertEquals(75, parsed.getInt("volumePercent"))
        val contacts = parsed.getJSONArray("contacts")
        assertEquals(2, contacts.length())
    }

    @Test
    fun importHandlesNullPassphrase() {
        val json = """{"version":1,"exportedAt":0,"passphrase":null,"passphraseCreatedAt":0,"ownPhoneNumber":"","ntfyServerUrl":"","ntfyAuthToken":"","volumePercent":100,"overrideSoundType":0,"contacts":[],"quietRules":[]}"""
        val restored = ConfigExporter.import(json)
        assertNotNull(restored)
        assertNull(restored!!.passphrase)
    }

    @Test
    fun importReturnsNullForInvalidJson() {
        assertNull(ConfigExporter.import(""))
        assertNull(ConfigExporter.import("not json"))
        assertNull(ConfigExporter.import("{ broken"))
    }

    @Test
    fun importHandlesMissingFieldsWithDefaults() {
        val json = """{"version":1}"""
        val restored = ConfigExporter.import(json)
        assertNotNull(restored)
        assertEquals("", restored!!.ownPhoneNumber)
        assertTrue(restored.contacts.isEmpty())
        assertTrue(restored.quietRules.isEmpty())
    }

    @Test
    fun importHandlesEmptyContactsAndRules() {
        val json = """{"version":1,"contacts":[],"quietRules":[]}"""
        val restored = ConfigExporter.import(json)
        assertNotNull(restored)
        assertTrue(restored!!.contacts.isEmpty())
        assertTrue(restored.quietRules.isEmpty())
    }

    @Test
    fun import_dropsRuleWithOutOfRangeHour() {
        val json = """
            {"version":1,"quietRules":[
              {"days":[2],"startHour":99,"startMinute":0,"endHour":6,"endMinute":0},
              {"days":[2],"startHour":22,"startMinute":0,"endHour":6,"endMinute":0}
            ]}
        """.trimIndent()
        val config = ConfigExporter.import(json)
        assertNotNull(config)
        assertEquals(1, config!!.quietRules.size)
        assertEquals(22, config.quietRules[0].startHour)
    }

    @Test
    fun isValidRuleRanges_validAndInvalid() {
        assertTrue(ConfigExporter.isValidRuleRanges(22, 0, 6, 30, setOf(1, 7)))
        assertFalse(ConfigExporter.isValidRuleRanges(24, 0, 6, 0, setOf(1)))
        assertFalse(ConfigExporter.isValidRuleRanges(0, 60, 6, 0, setOf(1)))
        assertFalse(ConfigExporter.isValidRuleRanges(0, 0, 6, 0, setOf(0)))
        assertFalse(ConfigExporter.isValidRuleRanges(0, 0, 6, 0, setOf(8)))
    }

    @Test
    fun isValidRuleRanges_rejectsZeroDurationRule() {
        assertFalse(ConfigExporter.isValidRuleRanges(22, 0, 22, 0, setOf(1)))
    }

    @Test
    fun import_dropsZeroDurationRule() {
        val json = """
            {"version":1,"quietRules":[
              {"days":[2],"startHour":22,"startMinute":0,"endHour":22,"endMinute":0},
              {"days":[2],"startHour":22,"startMinute":0,"endHour":6,"endMinute":0}
            ]}
        """.trimIndent()
        val config = ConfigExporter.import(json)
        assertNotNull(config)
        assertEquals(1, config!!.quietRules.size)
        assertEquals(6, config.quietRules[0].endHour)
    }
}
