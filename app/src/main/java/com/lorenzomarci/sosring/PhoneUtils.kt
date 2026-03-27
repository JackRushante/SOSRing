package com.lorenzomarci.sosring

object PhoneUtils {
    fun normalize(number: String): String {
        var n = number.replace(Regex("[\\s\\-().]"), "")
        // Remove leading 00 international prefix (0039 → +39)
        if (n.startsWith("00")) n = "+" + n.removePrefix("00")
        // Italian numbers without + prefix: 393... → +393...
        if (!n.startsWith("+") && n.startsWith("39") && n.length >= 11) n = "+$n"
        // Local Italian numbers: 3xx... → +39 3xx...
        if (!n.startsWith("+") && n.startsWith("3") && n.length == 10) n = "+39$n"
        return n
    }
}
