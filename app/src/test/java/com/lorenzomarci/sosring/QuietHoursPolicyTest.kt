package com.lorenzomarci.sosring

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursPolicyTest {
    private val daytime = QuietRule(setOf(Calendar.MONDAY), 9, 0, 18, 0)
    private val overnight = QuietRule(setOf(Calendar.FRIDAY), 22, 0, 6, 0)

    @Test fun sameDayScheduleIncludesStartAndExcludesEnd() {
        assertTrue(QuietHoursPolicy.isQuiet(listOf(daytime), Calendar.MONDAY, 9 * 60))
        assertTrue(QuietHoursPolicy.isQuiet(listOf(daytime), Calendar.MONDAY, 17 * 60 + 59))
        assertFalse(QuietHoursPolicy.isQuiet(listOf(daytime), Calendar.MONDAY, 18 * 60))
    }

    @Test fun overnightScheduleCoversBothCalendarDays() {
        assertTrue(QuietHoursPolicy.isQuiet(listOf(overnight), Calendar.FRIDAY, 23 * 60))
        assertTrue(QuietHoursPolicy.isQuiet(listOf(overnight), Calendar.SATURDAY, 5 * 60 + 59))
        assertFalse(QuietHoursPolicy.isQuiet(listOf(overnight), Calendar.SATURDAY, 6 * 60))
    }

    @Test fun scheduleDoesNotApplyOnUnselectedDay() {
        assertFalse(QuietHoursPolicy.isQuiet(listOf(daytime), Calendar.TUESDAY, 12 * 60))
    }
}
