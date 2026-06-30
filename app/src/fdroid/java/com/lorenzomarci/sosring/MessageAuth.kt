package com.lorenzomarci.sosring

import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey

object MessageAuth {

    fun sign(privateKey: ECPrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun verify(idPub: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(WebPushCrypto.loadPublic(idPub))
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    fun fingerprint(idPub: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(idPub)
        return WebPushCrypto.b64enc(hash.copyOf(8))
    }
}
