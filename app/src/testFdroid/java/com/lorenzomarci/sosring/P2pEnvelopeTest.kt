package com.lorenzomarci.sosring

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import org.junit.Test

class P2pEnvelopeTest {

    private fun idPubOf(publicKey: java.security.PublicKey) =
        WebPushCrypto.pointBytes(publicKey as ECPublicKey)

    @Test
    fun sealThenOpen_trustedRecoversPayload() {
        val signer = WebPushCrypto.generateKeyPair()
        val idPub = idPubOf(signer.public)
        val payload = """{"type":"loc_request"}""".toByteArray()

        val envelope = P2pEnvelope.seal(payload, idPub) { MessageAuth.sign(signer.private as ECPrivateKey, it) }
        val opened = P2pEnvelope.open(envelope) { true }

        assertNotNull(opened)
        assertArrayEquals(payload, opened!!.payload)
        assertArrayEquals(idPub, opened.senderIdPub)
    }

    @Test
    fun open_returnsNullWhenSenderUntrusted() {
        val signer = WebPushCrypto.generateKeyPair()
        val idPub = idPubOf(signer.public)
        val envelope = P2pEnvelope.seal("x".toByteArray(), idPub) { MessageAuth.sign(signer.private as ECPrivateKey, it) }

        assertNull(P2pEnvelope.open(envelope) { false })
    }

    @Test
    fun open_returnsNullOnTamperedPayload() {
        val signer = WebPushCrypto.generateKeyPair()
        val idPub = idPubOf(signer.public)
        val envelope = P2pEnvelope.seal("original".toByteArray(), idPub) { MessageAuth.sign(signer.private as ECPrivateKey, it) }

        val json = JSONObject(String(envelope, Charsets.UTF_8))
        json.put("p", WebPushCrypto.b64enc("tampered".toByteArray()))
        val tampered = json.toString().toByteArray(Charsets.UTF_8)

        assertNull(P2pEnvelope.open(tampered) { true })
    }
}
