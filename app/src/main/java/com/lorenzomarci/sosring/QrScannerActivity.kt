package com.lorenzomarci.sosring

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.lorenzomarci.sosring.databinding.ActivityQrScannerBinding
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var completed = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnCancelScanner.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeFrame(imageProxy) }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (completed) {
            imageProxy.close()
            return
        }
        try {
            val text = decodeImage(imageProxy)
            if (!text.isNullOrBlank()) {
                completed = true
                runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_QR_TEXT, text))
                    finish()
                }
            }
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    private fun decodeImage(imageProxy: ImageProxy): String? {
        val width = imageProxy.width
        val height = imageProxy.height
        val yBytes = copyYPlane(imageProxy)
        return QrYuvDecoder.decode(width, height, yBytes)
    }

    private fun copyYPlane(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val output = ByteArray(width * height)
        var outputOffset = 0
        val row = ByteArray(rowStride)

        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val bytesToRead = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, bytesToRead)
            var inputOffset = 0
            for (x in 0 until width) {
                output[outputOffset++] = row[inputOffset]
                inputOffset += pixelStride
            }
        }
        return output
    }

    companion object {
        const val EXTRA_QR_TEXT = "qr_text"
    }
}
