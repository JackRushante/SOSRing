package com.lorenzomarci.sosring

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object IdentityKeyStore {

    private const val ALIAS = "sosring_identity_v1"
    private const val KEYSTORE = "AndroidKeyStore"

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }

    fun idPub(): ByteArray {
        ensureKey()
        val certificate = keyStore().getCertificate(ALIAS)
        return WebPushCrypto.pointBytes(certificate.publicKey as ECPublicKey)
    }

    fun sign(data: ByteArray): ByteArray {
        ensureKey()
        val privateKey = keyStore().getKey(ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
}
