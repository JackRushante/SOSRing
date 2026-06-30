package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateUrlPolicyTest {

    @Test
    fun acceptsHttpsAndHttpUpdateUrls() {
        assertEquals(
            "https://example.test/app.apk",
            UpdateUrlPolicy.resolveApkUrl("https://updates.test/", "https://example.test/app.apk")
        )
        assertEquals(
            "http://example.com/update/app.apk",
            UpdateUrlPolicy.resolveApkUrl("http://example.com/update/", "app.apk")
        )
    }

    @Test
    fun rejectsNonHttpSchemesAndBlankValues() {
        assertNull(UpdateUrlPolicy.resolveApkUrl("", "app.apk"))
        assertNull(UpdateUrlPolicy.resolveApkUrl("http://example.com/update/", "file:///tmp/app.apk"))
        assertNull(UpdateUrlPolicy.resolveApkUrl("http://example.com/update/", "../app.apk"))
    }
}
