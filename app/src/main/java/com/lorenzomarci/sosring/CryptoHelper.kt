package com.lorenzomarci.sosring

import android.content.Context
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isConfigured(): Boolean {
        val passphrase = currentPassphrase()
        return !passphrase.isNullOrBlank()
    }

    fun deriveKey(myNumber: String, theirNumber: String): SecretKeySpec {
        val passphrase = currentPassphrase() ?: ""
        return deriveKeyWithPassphrase(myNumber, theirNumber, passphrase)
    }

    internal fun deriveKeyWithPassphrase(
        myNumber: String,
        theirNumber: String,
        passphrase: String
    ): SecretKeySpec {
        val sorted = listOf(PhoneUtils.normalize(myNumber), PhoneUtils.normalize(theirNumber)).sorted()
        val input = sorted[0] + sorted[1] + passphrase
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun keyFingerprint(): String? {
        val passphrase = currentPassphrase() ?: return null
        if (passphrase.isBlank()) return null
        return PassphraseHelper.fingerprint(passphrase)
    }

    fun encrypt(plaintext: String, myNumber: String, theirNumber: String): String {
        val key = deriveKey(myNumber, theirNumber)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encoded: String, myNumber: String, theirNumber: String): String? {
        return try {
            val key = deriveKey(myNumber, theirNumber)
            val combined = Base64.getDecoder().decode(encoded)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    internal fun encryptWithPassphrase(
        plaintext: String,
        myNumber: String,
        theirNumber: String,
        passphrase: String
    ): String {
        val key = deriveKeyWithPassphrase(myNumber, theirNumber, passphrase)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    internal fun decryptWithPassphrase(
        encoded: String,
        myNumber: String,
        theirNumber: String,
        passphrase: String
    ): String? {
        return try {
            val key = deriveKeyWithPassphrase(myNumber, theirNumber, passphrase)
            val combined = Base64.getDecoder().decode(encoded)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun currentPassphrase(): String? {
        val ctx = appContext ?: return null
        return PrefsManager(ctx).userPassphrase
    }
}

