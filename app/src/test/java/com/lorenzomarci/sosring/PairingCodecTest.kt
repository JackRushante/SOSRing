package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodecTest {

    @Test
    fun encode_withToken_roundTrips() {
        val encoded = PairingCodec.encode("PASS", "tk_abc")
        val payload = PairingCodec.decode(encoded)!!
        assertEquals("PASS", payload.passphrase)
        assertEquals("tk_abc", payload.token)
    }

    @Test
    fun encode_blankToken_omitsToken() {
        val encoded = PairingCodec.encode("PASS", "")
        val payload = PairingCodec.decode(encoded)!!
        assertEquals("PASS", payload.passphrase)
        assertNull(payload.token)
    }

    @Test
    fun decode_legacyRawPassphrase_hasNoToken() {
        val payload = PairingCodec.decode("Zm9vYmFyMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")!!
        assertEquals("Zm9vYmFyMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.passphrase)
        assertNull(payload.token)
    }

    @Test
    fun decode_blank_returnsNull() {
        assertNull(PairingCodec.decode("   "))
    }

    @Test
    fun decode_jsonWithoutPassphrase_returnsNull() {
        assertNull(PairingCodec.decode("{\"t\":\"tk_x\"}"))
    }
}
