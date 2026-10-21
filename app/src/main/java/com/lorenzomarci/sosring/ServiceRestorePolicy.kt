package com.lorenzomarci.sosring

import android.content.Intent

object ServiceRestorePolicy {
    fun shouldRestore(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}
