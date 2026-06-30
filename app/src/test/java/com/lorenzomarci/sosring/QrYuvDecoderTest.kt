package com.lorenzomarci.sosring

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class QrYuvDecoderTest {

    @Test
    fun decodesQrFromLuminancePlane() {
        val content = PassphraseHelper.generate()
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256)
        val yBytes = ByteArray(matrix.width * matrix.height)

        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                yBytes[y * matrix.width + x] = if (matrix[x, y]) 0 else 255.toByte()
            }
        }

        assertEquals(content, QrYuvDecoder.decode(matrix.width, matrix.height, yBytes))
    }
}
