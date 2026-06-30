package com.lorenzomarci.sosring

import android.content.Context

class UpdateChecker(private val context: Context) {
    fun checkAndNotify(force: Boolean = false) {}
    fun downloadAndInstall(apkUrl: String) {}
}
