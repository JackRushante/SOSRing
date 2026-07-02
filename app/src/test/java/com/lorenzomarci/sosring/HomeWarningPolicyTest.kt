package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWarningPolicyTest {
    @Test fun serviceOff_none() =
        assertEquals(HomeWarning.NONE, HomeWarningPolicy.decide(serviceEnabled = false, criticalMissing = true, autoRevokeActive = true))
    @Test fun revoked_takesPriority() =
        assertEquals(HomeWarning.PERMISSIONS_REVOKED, HomeWarningPolicy.decide(serviceEnabled = true, criticalMissing = true, autoRevokeActive = true))
    @Test fun autoRevoke_whenNoRevocation() =
        assertEquals(HomeWarning.AUTO_REVOKE, HomeWarningPolicy.decide(serviceEnabled = true, criticalMissing = false, autoRevokeActive = true))
    @Test fun allGood_none() =
        assertEquals(HomeWarning.NONE, HomeWarningPolicy.decide(serviceEnabled = true, criticalMissing = false, autoRevokeActive = false))

    // BATTERY_UNRESTRICTED_NEEDED: lowest priority, only shown when no other warning applies.
    @Test fun batteryUnrestrictedNeeded_whenNoOtherWarning() =
        assertEquals(
            HomeWarning.BATTERY_UNRESTRICTED_NEEDED,
            HomeWarningPolicy.decide(
                serviceEnabled = true, criticalMissing = false, autoRevokeActive = false,
                batteryOptimizationActive = true
            )
        )
    @Test fun revoked_takesPriorityOverBattery() =
        assertEquals(
            HomeWarning.PERMISSIONS_REVOKED,
            HomeWarningPolicy.decide(
                serviceEnabled = true, criticalMissing = true, autoRevokeActive = false,
                batteryOptimizationActive = true
            )
        )
    @Test fun autoRevoke_takesPriorityOverBattery() =
        assertEquals(
            HomeWarning.AUTO_REVOKE,
            HomeWarningPolicy.decide(
                serviceEnabled = true, criticalMissing = false, autoRevokeActive = true,
                batteryOptimizationActive = true
            )
        )
    @Test fun serviceOff_none_evenWithBatteryActive() =
        assertEquals(
            HomeWarning.NONE,
            HomeWarningPolicy.decide(
                serviceEnabled = false, criticalMissing = true, autoRevokeActive = true,
                batteryOptimizationActive = true
            )
        )
    @Test fun allGood_none_whenBatteryUnrestrictedAlready() =
        assertEquals(
            HomeWarning.NONE,
            HomeWarningPolicy.decide(
                serviceEnabled = true, criticalMissing = false, autoRevokeActive = false,
                batteryOptimizationActive = false
            )
        )
}
