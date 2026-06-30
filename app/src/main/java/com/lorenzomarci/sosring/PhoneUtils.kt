package com.lorenzomarci.sosring

object PhoneUtils {
    fun normalize(number: String): String {
        var n = number.replace(Regex("[\\s\\-().]"), "")
        if (n.startsWith("00")) n = "+" + n.removePrefix("00")
        if (!n.startsWith("+") && n.startsWith("39") && n.length >= 11) n = "+$n"
        if (!n.startsWith("+") && n.startsWith("3") && n.length == 10) n = "+39$n"
        return n
    }

    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        return na.isNotBlank() && na == normalize(b)
    }
}
