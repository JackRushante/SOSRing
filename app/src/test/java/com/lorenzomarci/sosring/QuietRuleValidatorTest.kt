package com.lorenzomarci.sosring

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietRuleValidatorTest {

    @Test
    fun acceptsRulesWithAtLeastOneDayAndDifferentTimes() {
        assertTrue(
            QuietRuleValidator.isValid(
                days = setOf(Calendar.MONDAY),
                startHour = 22,
                startMinute = 0,
                endHour = 6,
                endMinute = 0
            )
        )
    }

    @Test
    fun rejectsEmptyDaysAndZeroDurationRules() {
        assertFalse(
            QuietRuleValidator.isValid(
                days = emptySet(),
                startHour = 9,
                startMinute = 0,
                endHour = 18,
                endMinute = 0
            )
        )
        assertFalse(
            QuietRuleValidator.isValid(
                days = setOf(Calendar.MONDAY),
                startHour = 9,
                startMinute = 0,
                endHour = 9,
                endMinute = 0
            )
        )
    }
}
