package com.lorenzomarci.sosring

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val APP_SECRET = "***REDACTED_SECRET***"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    fun deriveKey(myNumber: String, theirNumber: String): SecretKeySpec {
        val sorted = listOf(PhoneUtils.normalize(myNumber), PhoneUtils.normalize(theirNumber)).sorted()
        val input = sorted[0] + sorted[1] + APP_SECRET
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String, myNumber: String, theirNumber: String): String {
        val key = deriveKey(myNumber, theirNumber)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // auto-generated 12-byte IV
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Prepend IV to ciphertext (IV + ciphertext + GCM tag)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, myNumber: String, theirNumber: String): String? {
        return try {
            val key = deriveKey(myNumber, theirNumber)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
