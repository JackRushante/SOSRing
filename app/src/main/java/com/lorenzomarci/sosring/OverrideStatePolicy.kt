package com.lorenzomarci.sosring

object OverrideStatePolicy {
    fun shouldRestoreOnStart(persistedOverriding: Boolean, callStateIdle: Boolean): Boolean {
        return persistedOverriding && callStateIdle
    }
}
