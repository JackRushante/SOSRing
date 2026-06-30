package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedPushPairingTest {

    @Test
    fun roundTrip_withKeyFingerprint() {
        val payload = PairPayload(
            endpoint = "https://example.com/upABC123",
            p256dh = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            auth = "BTBZMqHH6r4Tts7J_aSIgg",
            idPub = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
        )

        val decoded = UnifiedPushPairing.decode(UnifiedPushPairing.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun roundTrip_withoutIdentityKey() {
        val payload = PairPayload(
            endpoint = "https://push.example.com/up1",
            p256dh = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
            auth = "DGv6ra1nlYgDCS1FRnbzlw"
        )

        val decoded = UnifiedPushPairing.decode(UnifiedPushPairing.encode(payload))

        assertEquals(payload, decoded)
        assertNull(decoded?.idPub)
    }

    @Test
    fun decode_rejectsWrongPrefix() {
        assertNull(UnifiedPushPairing.decode("https://push.example.com/up1"))
    }

    @Test
    fun decode_rejectsGarbage() {
        assertNull(UnifiedPushPairing.decode("sosup1:not-valid-base64-or-json!!!"))
    }

    @Test
    fun decode_rejectsMissingRequiredField() {
        val incomplete = "sosup1:" + WebPushCrypto.b64enc("""{"e":"https://x/up1","p":"key"}""".toByteArray())
        assertNull(UnifiedPushPairing.decode(incomplete))
    }
}
