package com.lorenzomarci.sosring

enum class HomeWarning { NONE, PERMISSIONS_REVOKED, AUTO_REVOKE, BATTERY_UNRESTRICTED_NEEDED }

object HomeWarningPolicy {
    fun decide(
        serviceEnabled: Boolean,
        criticalMissing: Boolean,
        autoRevokeActive: Boolean,
        batteryOptimizationActive: Boolean = false
    ): HomeWarning {
        return when {
            !serviceEnabled -> HomeWarning.NONE
            criticalMissing -> HomeWarning.PERMISSIONS_REVOKED
            autoRevokeActive -> HomeWarning.AUTO_REVOKE
            batteryOptimizationActive -> HomeWarning.BATTERY_UNRESTRICTED_NEEDED
            else -> HomeWarning.NONE
        }
    }
}
