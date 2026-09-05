package com.lorenzomarci.sosring

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RepeatCallConfigTest {
    @Test fun oldBackupsKeepImmediateCallsAndNoEscalation() {
        val config = ConfigExporter.import("""{"contacts":[{"name":"Alarm","number":"+12025550101"}]}""")!!
        assertEquals(CallAlertMode.FIRST, config.callAlertMode)
        assertEquals(5, config.repeatCallWindowMinutes)
        assertFalse(config.escalateCallVolume)
        assertEquals(CallAlertMode.INHERIT, config.contacts.single().callAlertMode)
    }

    @Test fun settingsAndPerVipExceptionsRoundTripWithoutCallHistory() {
        val old = ConfigExporter.import("{}")!!
        val config = old.copy(callAlertMode = CallAlertMode.SECOND, repeatCallWindowMinutes = 3,
            escalateCallVolume = true, contacts = listOf(VipContact("Alarm", "+12025550101", callAlertMode = CallAlertMode.FIRST)))
        val encoded = ConfigExporter.export(config)
        assertEquals(config, ConfigExporter.import(encoded))
        assertFalse(encoded.contains("history"))
    }

    @Test fun malformedEnumsAndWindowsFallbackSafely() {
        val config = ConfigExporter.import("""{"callAlertMode":"unknown","repeatCallWindowMinutes":999}""")!!
        assertEquals(CallAlertMode.FIRST, config.callAlertMode)
        assertEquals(5, config.repeatCallWindowMinutes)
    }

    @Test fun shortQuietPeriodBetweenCallsResetsEvenWhenNeitherCallIsQuiet() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 7, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis
        val rules = listOf(QuietRule(setOf(Calendar.MONDAY), 12, 1, 12, 2))
        assertTrue(QuietHoursPolicy.wasQuietBetween(rules, start, start + 180_000))
        assertFalse(QuietHoursPolicy.wasQuietBetween(rules, start + 120_000, start + 180_000))
    }
}
