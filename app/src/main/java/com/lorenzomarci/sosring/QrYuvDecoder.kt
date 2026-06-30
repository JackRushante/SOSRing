package com.lorenzomarci.sosring

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrYuvDecoder {
    fun decode(width: Int, height: Int, yBytes: ByteArray): String? {
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
        val source = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.trim()
        } catch (_: NotFoundException) {
            decodeRotated(reader, source)
        } finally {
            reader.reset()
        }
    }

    private fun decodeRotated(
        reader: MultiFormatReader,
        source: PlanarYUVLuminanceSource
    ): String? {
        if (!source.isRotateSupported) return null
        return try {
            reader.reset()
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.rotateCounterClockwise()))).text.trim()
        } catch (_: NotFoundException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }
}
