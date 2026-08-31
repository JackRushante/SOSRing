package com.lorenzomarci.sosring

import java.util.Calendar

object QuietHoursPolicy {
    fun isQuiet(
        rules: List<QuietRule>,
        currentDay: Int,
        currentMinuteOfDay: Int
    ): Boolean {
        val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY
            else if (currentDay == Calendar.MONDAY) Calendar.SUNDAY
            else currentDay - 1

        return rules.any { rule ->
            val start = rule.startHour * 60 + rule.startMinute
            val end = rule.endHour * 60 + rule.endMinute
            if (end > start) {
                currentDay in rule.days && currentMinuteOfDay >= start && currentMinuteOfDay < end
            } else {
                (currentDay in rule.days && currentMinuteOfDay >= start) ||
                    (previousDay in rule.days && currentMinuteOfDay < end)
            }
        }
    }
}
