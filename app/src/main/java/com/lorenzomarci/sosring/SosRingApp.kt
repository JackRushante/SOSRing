package com.lorenzomarci.sosring

import android.app.Application

class SosRingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CryptoHelper.init(this)
        PrefsManager(this).migrateSecretsIfNeeded()
    }
}
