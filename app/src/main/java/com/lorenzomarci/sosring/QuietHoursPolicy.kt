package com.lorenzomarci.sosring

import java.util.Calendar

object QuietHoursPolicy {
    fun wasQuietBetween(rules: List<QuietRule>, fromMillis: Long, toMillis: Long): Boolean {
        if (rules.isEmpty()) return false
        if (toMillis < fromMillis || toMillis - fromMillis > 10 * 60_000L) return true
        val cal = Calendar.getInstance()
        var time = fromMillis
        while (true) {
            cal.timeInMillis = time
            if (isQuiet(rules, cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) return true
            if (time >= toMillis) return false
            time = minOf(toMillis, (time / 60_000L + 1) * 60_000L)
        }
    }

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
