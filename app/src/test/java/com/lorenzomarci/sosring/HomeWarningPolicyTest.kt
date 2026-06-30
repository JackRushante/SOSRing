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
}
