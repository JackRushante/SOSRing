package com.lorenzomarci.sosring

enum class P2pBlock {
    NONE,
    NOT_REGISTERED,
    NOT_PAIRED
}

object P2pLocationReadiness {
    fun check(registered: Boolean, peerPaired: Boolean): P2pBlock {
        return when {
            !registered -> P2pBlock.NOT_REGISTERED
            !peerPaired -> P2pBlock.NOT_PAIRED
            else -> P2pBlock.NONE
        }
    }
}
