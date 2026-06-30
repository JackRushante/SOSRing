package com.lorenzomarci.sosring

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PassphraseHelper {

    private const val PASSPHRASE_BYTES = 32

    fun generate(): String {
        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun fingerprint(passphrase: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(passphrase.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)
    }

    fun isValid(passphrase: String): Boolean {
        if (passphrase.isBlank()) return false
        return try {
            Base64.getDecoder().decode(passphrase).size == PASSPHRASE_BYTES
        } catch (e: Exception) {
            false
        }
    }
}
