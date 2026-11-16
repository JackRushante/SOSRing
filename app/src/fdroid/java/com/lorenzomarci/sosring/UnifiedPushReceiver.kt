package com.lorenzomarci.sosring

import android.util.Log
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class UnifiedPushReceiver : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val store = UnifiedPushStore(this)
        val previousUrl = store.endpointUrl
        val previousPubKey = store.pubKey
        val previousAuth = store.auth
        val pubKey = endpoint.pubKeySet?.pubKey
        val auth = endpoint.pubKeySet?.auth
        store.saveEndpoint(url = endpoint.url, pubKey = pubKey, auth = auth)
        Log.i(TAG, "UnifiedPush endpoint registered")
        // Peers paired with the old address or keys could no longer reach this phone: hand them
        // the new ones (signed with the paired identity) instead of letting the pairing go stale.
        val changed = previousUrl != endpoint.url || previousPubKey != pubKey || previousAuth != auth
        if (!previousUrl.isNullOrBlank() && changed && !pubKey.isNullOrBlank() && !auth.isNullOrBlank()) {
            Log.i(TAG, "UnifiedPush endpoint changed, updating paired peers")
            P2pMessaging.broadcastEndpoint(this, endpoint.url, pubKey, auth)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            // A peer encrypted with keys this phone no longer has: its pairing is stale.
            Log.w(TAG, "Received undecrypted push message, ignoring")
            P2pMessaging.notice(this, "undecrypted", R.string.p2p_notice_undecrypted)
            return
        }
        P2pMessaging.handleIncoming(this, message.content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.e(TAG, "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        UnifiedPushStore(this).clear()
        Log.i(TAG, "UnifiedPush unregistered")
    }

    companion object {
        private const val TAG = "UnifiedPushReceiver"
    }
}
