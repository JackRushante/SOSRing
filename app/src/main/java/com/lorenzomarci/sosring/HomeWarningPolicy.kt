package com.lorenzomarci.sosring

enum class HomeWarning { NONE, PERMISSIONS_REVOKED, AUTO_REVOKE }

object HomeWarningPolicy {
    fun decide(serviceEnabled: Boolean, criticalMissing: Boolean, autoRevokeActive: Boolean): HomeWarning {
        return when {
            !serviceEnabled -> HomeWarning.NONE
            criticalMissing -> HomeWarning.PERMISSIONS_REVOKED
            autoRevokeActive -> HomeWarning.AUTO_REVOKE
            else -> HomeWarning.NONE
        }
    }
}
