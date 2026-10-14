package com.lorenzomarci.sosring

import android.app.Application

class SosRingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsManager(this).initCallRamp(BuildConfig.CALL_RAMP_DEFAULT)
        ThemeManager.applyNightMode(PrefsManager(this).themeMode)
        CryptoHelper.init(this)
        PrefsManager(this).migrateSecretsIfNeeded()
    }
}
