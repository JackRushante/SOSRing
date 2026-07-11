package com.lorenzomarci.sosring

import android.app.Application

class SosRingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyNightMode(PrefsManager(this).themeMode)
        CryptoHelper.init(this)
        PrefsManager(this).migrateSecretsIfNeeded()
    }
}
