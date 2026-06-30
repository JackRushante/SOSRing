package com.lorenzomarci.sosring

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecretStore {

    private const val TAG = "SecretStore"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sosring_secret_v1"
    private const val MARKER = "enc1:"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val AES_GCM = "AES/GCM/NoPadding"

    fun isWrapped(value: String?): Boolean = value?.startsWith(MARKER) == true

    fun wrap(plain: String): String {
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            MARKER + Base64.getEncoder().encodeToString(iv + ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "wrap failed, storing plaintext: ${e.message}")
            plain
        }
    }

    fun unwrap(value: String?): String? {
        if (value == null) return null
        if (!value.startsWith(MARKER)) return value
        return try {
            val combined = Base64.getDecoder().decode(value.removePrefix(MARKER))
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "unwrap failed: ${e.message}")
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
