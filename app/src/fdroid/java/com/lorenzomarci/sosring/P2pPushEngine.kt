package com.lorenzomarci.sosring

import android.content.Context
import android.util.Log

class P2pPushEngine(private val appContext: Context) : PushEngine {

    override val liveContactNumber: String? get() = P2pLiveController.outgoingContactNumber

    override fun requestLocation(contact: VipContact) {
        val peer = PeerStore(appContext).get(contact.number)
        if (peer == null) {
            Log.w(TAG, "No paired peer for requested contact")
            return
        }
        Thread { P2pMessaging.requestLocation(appContext, peer) }.start()
    }

    override fun runDiscovery() {}

    override fun startLiveTracking(contact: VipContact, durationMinutes: Int): Boolean =
        P2pLiveController.startOutgoing(appContext, contact, durationMinutes)

    override fun stopLiveTracking() = P2pLiveController.stopOutgoing(appContext, sendEnd = true)

    override fun getLiveSessionId(): String? = P2pLiveController.outgoingSessionId()

    override fun heartbeat(nowMs: Long) = P2pLiveController.heartbeat(appContext, nowMs)

    override fun resubscribe() {}

    override fun broadcastKeyRotated() {}

    override fun stop() {}

    companion object {
        private const val TAG = "P2pPushEngine"
    }
}
