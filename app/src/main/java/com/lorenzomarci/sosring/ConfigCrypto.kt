package com.lorenzomarci.sosring

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ConfigCrypto {

    private const val FORMAT = "sosring-enc-v1"
    private const val ITERATIONS = 600000
    private const val MIN_ITERATIONS = 100000
    private const val MAX_ITERATIONS = 1000000
    private const val KEY_BITS = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val AES_GCM = "AES/GCM/NoPadding"

    fun encrypt(plaintext: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return JSONObject().apply {
            put("format", FORMAT)
            put("iter", ITERATIONS)
            put("salt", enc.encodeToString(salt))
            put("iv", enc.encodeToString(iv))
            put("data", enc.encodeToString(ciphertext))
        }.toString()
    }

    fun decrypt(envelope: String, password: String): String? {
        return try {
            val root = JSONObject(envelope)
            if (root.optString("format") != FORMAT) return null
            val iterations = root.getInt("iter")
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) return null
            val dec = Base64.getDecoder()
            val salt = dec.decode(root.getString("salt"))
            val iv = dec.decode(root.getString("iv"))
            val ciphertext = dec.decode(root.getString("data"))
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun isEncryptedEnvelope(text: String): Boolean {
        return try {
            JSONObject(text).optString("format") == FORMAT
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
