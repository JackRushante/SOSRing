package com.lorenzomarci.sosring

object PhoneUtils {
    fun normalize(number: String): String {
        var n = number.replace(Regex("[\\s\\-().]"), "")
        if (n.startsWith("00")) n = "+" + n.removePrefix("00")
        if (!n.startsWith("+") && n.startsWith("39") && n.length >= 11) n = "+$n"
        if (!n.startsWith("+") && n.startsWith("3") && n.length == 10 && n[1] !in '0'..'1') n = "+39$n"
        return n
    }

    private fun digitForms(s: String): List<String> {
        val raw = s.filter { it.isDigit() }
        val stripped = raw.trimStart('0')
        return if (stripped == raw) listOf(raw) else listOf(raw, stripped)
    }

    private fun suffixMatch(x: String, y: String): Boolean {
        val n = minOf(x.length, y.length)
        return n >= 8 && x.takeLast(n) == y.takeLast(n)
    }

    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        if (na.isBlank()) return false
        val nb = normalize(b)
        if (na == nb) return true
        return digitForms(na).any { da -> digitForms(nb).any { db -> suffixMatch(da, db) } }
    }
}
