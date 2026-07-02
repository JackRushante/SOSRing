package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerStoreTest {

    private fun peer(number: String, idPub: String) = Peer(
        number = number,
        endpoint = "https://push.example.test/endpoint",
        p256dh = "p256dh",
        auth = "auth",
        idPub = idPub
    )

    @Test
    fun matchSingleByIdPub_returnsNullWhenTwoPeersShareIdPub() {
        val sharedIdPub = "sharedIdPub"
        val peers = listOf(
            peer("+391111111111", sharedIdPub),
            peer("+392222222222", sharedIdPub)
        )

        assertNull(PeerStore.matchSingleByIdPub(peers, sharedIdPub))
    }

    @Test
    fun matchSingleByIdPub_returnsTheSingleMatch() {
        val peers = listOf(
            peer("+391111111111", "idPubA"),
            peer("+392222222222", "idPubB")
        )

        assertEquals("+391111111111", PeerStore.matchSingleByIdPub(peers, "idPubA")?.number)
    }

    @Test
    fun matchSingleByIdPub_returnsNullWhenNoMatch() {
        val peers = listOf(peer("+391111111111", "idPubA"))

        assertNull(PeerStore.matchSingleByIdPub(peers, "idPubZ"))
    }
}
