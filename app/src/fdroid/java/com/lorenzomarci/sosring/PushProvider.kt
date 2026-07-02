package com.lorenzomarci.sosring

import android.app.Activity
import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush

object PushProvider {

    const val supportsLiveTracking: Boolean = true
    const val supportsServerConfig: Boolean = false

    private var engineInstance: P2pPushEngine? = null

    fun canStart(context: Context): Boolean = true

    fun start(context: Context) {
        if (engineInstance == null) {
            engineInstance = P2pPushEngine(context.applicationContext)
        }
    }

    fun clear() {
        engineInstance = null
    }

    fun engine(): PushEngine? = engineInstance

    fun liveEngine(): PushEngine? = engineInstance ?: CallMonitorService.getInstance()?.pushEngine

    fun requestLocation(context: Context, contact: VipContact): Boolean {
        val engine = engineInstance ?: P2pPushEngine(context.applicationContext)
        engine.requestLocation(contact)
        return true
    }

    fun canRequestLocation(context: Context, number: String): Boolean {
        val registered = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()
        val peerPaired = PeerStore(context).get(number) != null
        return P2pLocationReadiness.check(registered, peerPaired) == P2pBlock.NONE
    }

    fun startLiveTracking(context: Context, contact: VipContact, minutes: Int): Boolean {
        val engine = engineInstance ?: P2pPushEngine(context.applicationContext).also { engineInstance = it }
        return engine.startLiveTracking(contact, minutes)
    }

    fun verifySetup(context: Context): PushSetupStatus = PushSetupStatus.SERVER_UNREACHABLE

    fun locationBlock(context: Context, contact: VipContact): String? {
        val registered = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()
        val peerPaired = PeerStore(context).get(contact.number) != null
        return when (P2pLocationReadiness.check(registered, peerPaired)) {
            P2pBlock.NONE -> null
            P2pBlock.NOT_REGISTERED -> context.getString(R.string.p2p_block_not_registered)
            P2pBlock.NOT_PAIRED -> context.getString(R.string.p2p_block_not_paired)
        }
    }

    fun onLocationSharingRevoked(context: Context, number: String) {
        P2pLiveController.stopIncomingIfRequester(context, number)
    }

    fun ensureRegistered(activity: Activity) {
        val ack = UnifiedPush.getAckDistributor(activity)
        val saved = UnifiedPush.getSavedDistributor(activity)
        val distributors = UnifiedPush.getDistributors(activity)
        Log.i("PushProvider", "ensureRegistered ack=$ack saved=$saved distributors=$distributors")
        if (ack != null) return
        val distributor = distributors.firstOrNull() ?: return
        UnifiedPush.saveDistributor(activity, distributor)
        UnifiedPush.register(activity)
        Log.i("PushProvider", "register requested via $distributor")
    }
}
