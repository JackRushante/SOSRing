package com.lorenzomarci.sosring

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRestorePolicyTest {

    @Test fun bootCompleted_restores() {
        assertTrue(ServiceRestorePolicy.shouldRestore(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test fun appUpdated_restores() {
        assertTrue(ServiceRestorePolicy.shouldRestore(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test fun otherPackageReplaced_doesNotRestore() {
        assertFalse(ServiceRestorePolicy.shouldRestore(Intent.ACTION_PACKAGE_REPLACED))
    }

    @Test fun missingAction_doesNotRestore() {
        assertFalse(ServiceRestorePolicy.shouldRestore(null))
    }
}
