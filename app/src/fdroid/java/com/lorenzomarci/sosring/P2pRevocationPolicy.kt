package com.lorenzomarci.sosring

object P2pRevocationPolicy {
    fun matchesActiveRequester(activeRequesterNumber: String?, revokedNumber: String): Boolean {
        if (activeRequesterNumber == null) return false
        return PhoneUtils.matches(activeRequesterNumber, revokedNumber)
    }
}
