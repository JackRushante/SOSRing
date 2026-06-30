package com.lorenzomarci.sosring

object QuietRuleValidator {
    fun isValid(
        days: Set<Int>,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Boolean {
        if (days.isEmpty()) return false
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        return start != end
    }
}
