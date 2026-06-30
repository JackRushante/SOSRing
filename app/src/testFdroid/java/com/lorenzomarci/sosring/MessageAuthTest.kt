package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import org.junit.Test

class MessageAuthTest {

    private fun idPubOf(publicKey: java.security.PublicKey) =
        WebPushCrypto.pointBytes(publicKey as ECPublicKey)

    @Test
    fun signThenVerify_succeeds() {
        val keyPair = WebPushCrypto.generateKeyPair()
        val data = "loc_request".toByteArray()

        val signature = MessageAuth.sign(keyPair.private as ECPrivateKey, data)

        assertTrue(MessageAuth.verify(idPubOf(keyPair.public), data, signature))
    }

    @Test
    fun verify_failsOnTamperedData() {
        val keyPair = WebPushCrypto.generateKeyPair()
        val signature = MessageAuth.sign(keyPair.private as ECPrivateKey, "loc_request".toByteArray())

        assertFalse(MessageAuth.verify(idPubOf(keyPair.public), "loc_response".toByteArray(), signature))
    }

    @Test
    fun verify_failsWithDifferentSignerKey() {
        val signer = WebPushCrypto.generateKeyPair()
        val impostor = WebPushCrypto.generateKeyPair()
        val data = "payload".toByteArray()

        val signature = MessageAuth.sign(signer.private as ECPrivateKey, data)

        assertFalse(MessageAuth.verify(idPubOf(impostor.public), data, signature))
    }

    @Test
    fun fingerprint_isStableAndShort() {
        val idPub = idPubOf(WebPushCrypto.generateKeyPair().public)

        assertEquals(MessageAuth.fingerprint(idPub), MessageAuth.fingerprint(idPub))
        assertEquals(11, MessageAuth.fingerprint(idPub).length)
    }
}
