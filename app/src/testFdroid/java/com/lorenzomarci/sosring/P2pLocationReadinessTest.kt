package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class P2pLocationReadinessTest {

    @Test
    fun notRegisteredWhenNoEndpoint() {
        assertEquals(
            P2pBlock.NOT_REGISTERED,
            P2pLocationReadiness.check(registered = false, peerPaired = false)
        )
    }

    @Test
    fun notRegisteredTakesPrecedenceOverPairing() {
        assertEquals(
            P2pBlock.NOT_REGISTERED,
            P2pLocationReadiness.check(registered = false, peerPaired = true)
        )
    }

    @Test
    fun notPairedWhenRegisteredButPeerMissing() {
        assertEquals(
            P2pBlock.NOT_PAIRED,
            P2pLocationReadiness.check(registered = true, peerPaired = false)
        )
    }

    @Test
    fun noneWhenRegisteredAndPaired() {
        assertEquals(
            P2pBlock.NONE,
            P2pLocationReadiness.check(registered = true, peerPaired = true)
        )
    }
}
