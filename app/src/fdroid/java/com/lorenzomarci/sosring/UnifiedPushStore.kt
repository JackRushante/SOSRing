package com.lorenzomarci.sosring

import android.content.Context

class UnifiedPushStore(context: Context) {

    private val prefs = context.getSharedPreferences("sosring_unifiedpush", Context.MODE_PRIVATE)

    val endpointUrl: String?
        get() = prefs.getString(KEY_ENDPOINT, null)

    val pubKey: String?
        get() = prefs.getString(KEY_PUBKEY, null)

    val auth: String?
        get() = prefs.getString(KEY_AUTH, null)

    fun saveEndpoint(url: String, pubKey: String?, auth: String?) {
        prefs.edit()
            .putString(KEY_ENDPOINT, url)
            .putString(KEY_PUBKEY, pubKey)
            .putString(KEY_AUTH, auth)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ENDPOINT)
            .remove(KEY_PUBKEY)
            .remove(KEY_AUTH)
            .apply()
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint_url"
        private const val KEY_PUBKEY = "pub_key"
        private const val KEY_AUTH = "auth"
    }
}
