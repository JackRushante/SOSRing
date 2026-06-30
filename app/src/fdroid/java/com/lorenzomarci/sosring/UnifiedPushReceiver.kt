package com.lorenzomarci.sosring

import android.util.Log
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class UnifiedPushReceiver : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        UnifiedPushStore(this).saveEndpoint(
            url = endpoint.url,
            pubKey = endpoint.pubKeySet?.pubKey,
            auth = endpoint.pubKeySet?.auth
        )
        Log.i(TAG, "UnifiedPush endpoint registered")
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            Log.w(TAG, "Received undecrypted push message, ignoring")
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
