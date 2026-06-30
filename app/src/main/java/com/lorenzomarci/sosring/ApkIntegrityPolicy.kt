package com.lorenzomarci.sosring

object ApkIntegrityPolicy {
    private val SHA256_REGEX = Regex("\\b[a-fA-F0-9]{64}\\b")

    fun isVerified(expectedSha: String?, actualSha: String): Boolean {
        val expected = normalizeExpectedSha(expectedSha)
        if (expected.isNullOrBlank()) return false
        return expected.equals(actualSha.trim(), ignoreCase = true)
    }

    private fun normalizeExpectedSha(expectedSha: String?): String? {
        val value = expectedSha?.trim()
        if (value.isNullOrBlank()) return null
        return SHA256_REGEX.find(value)?.value ?: value
    }
}
