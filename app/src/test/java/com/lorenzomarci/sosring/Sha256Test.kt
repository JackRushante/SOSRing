package com.lorenzomarci.sosring

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun sha256OfKnownString() {
        val tempFile = File.createTempFile("sha256-test", ".txt")
        tempFile.writeText("hello world")
        try {
            val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
            val actual = sha256OfFileForTest(tempFile)
            assertEquals(expected, actual)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun sha256OfEmptyString() {
        val tempFile = File.createTempFile("sha256-test-empty", ".txt")
        try {
            val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            val actual = sha256OfFileForTest(tempFile)
            assertEquals(expected, actual)
        } finally {
            tempFile.delete()
        }
    }

    private fun sha256OfFileForTest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
