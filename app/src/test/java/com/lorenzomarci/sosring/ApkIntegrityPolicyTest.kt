package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkIntegrityPolicyTest {

    @Test
    fun acceptsMatchingShaIgnoringCaseAndWhitespace() {
        assertTrue(
            ApkIntegrityPolicy.isVerified(
                expectedSha = " ABCDEF1234 \n",
                actualSha = "abcdef1234"
            )
        )
    }

    @Test
    fun acceptsStandardSha256sumLineWithFilename() {
        assertTrue(
            ApkIntegrityPolicy.isVerified(
                expectedSha = "9789bddc97ebf8f378dc3098c649fc60925e595477ad01334a34aedafb9c59cd  SOSRing-internal-release-2.7.8.apk\n",
                actualSha = "9789bddc97ebf8f378dc3098c649fc60925e595477ad01334a34aedafb9c59cd"
            )
        )
    }

    @Test
    fun rejectsMissingShaFile() {
        assertFalse(
            ApkIntegrityPolicy.isVerified(
                expectedSha = null,
                actualSha = "abcdef1234"
            )
        )
    }

    @Test
    fun rejectsMismatch() {
        assertFalse(
            ApkIntegrityPolicy.isVerified(
                expectedSha = "abcdef1234",
                actualSha = "0000000000"
            )
        )
    }
}
