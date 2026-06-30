package com.lorenzomarci.sosring

enum class LocationShareBlockReason {
    NONE,
    MISSING_OWN_NUMBER,
    MISSING_SERVER,
    MISSING_KEY
}

object LocationShareReadiness {
    fun check(
        ownPhoneNumber: String,
        serverUrl: String,
        encryptionConfigured: Boolean
    ): LocationShareBlockReason {
        return when {
            ownPhoneNumber.isBlank() -> LocationShareBlockReason.MISSING_OWN_NUMBER
            serverUrl.isBlank() -> LocationShareBlockReason.MISSING_SERVER
            !encryptionConfigured -> LocationShareBlockReason.MISSING_KEY
            else -> LocationShareBlockReason.NONE
        }
    }
}
