package com.lorenzomarci.sosring

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.lorenzomarci.sosring.databinding.DialogShowPassphraseBinding
import com.lorenzomarci.sosring.databinding.FragmentSecurityBinding

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    private val qrImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importKeyFromQrImage(uri)
    }

    private val qrCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val content = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_TEXT)
        if (result.resultCode == android.app.Activity.RESULT_OK && !content.isNullOrBlank()) {
            importPassphrase(content.trim())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        refreshKeyUI()

        binding.btnGenerateKey.setOnClickListener {
            if (prefs.userPassphrase.isNullOrBlank()) {
                generateKey(rotate = false)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.security_change_phone)
                    .setMessage(R.string.security_warning_rotate)
                    .setPositiveButton(R.string.security_generate) { _, _ -> generateKey(rotate = true) }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        binding.btnShowKey.setOnClickListener { showKeyDialog() }

        binding.btnImportKey.setOnClickListener { chooseImportMethod() }

        binding.btnChangePhone.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.security_change_phone)
                .setMessage(R.string.security_warning_rotate)
                .setPositiveButton(R.string.security_change_phone) { _, _ -> generateKey(rotate = true) }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshKeyUI() {
        val passphrase = prefs.userPassphrase
        if (passphrase.isNullOrBlank()) {
            binding.tvKeyStatus.text = getString(R.string.security_no_key)
            binding.btnShowKey.isEnabled = false
            binding.btnChangePhone.isEnabled = false
            binding.btnGenerateKey.text = getString(R.string.security_generate)
        } else {
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(prefs.passphraseCreatedAt))
            binding.tvKeyStatus.text = getString(R.string.security_key_created, date)
            binding.btnShowKey.isEnabled = true
            binding.btnChangePhone.isEnabled = true
            binding.btnGenerateKey.text = getString(R.string.security_change_phone)
        }
    }

    private fun generateKey(rotate: Boolean) {
        val passphrase = PassphraseHelper.generate()
        prefs.userPassphrase = passphrase
        prefs.passphraseCreatedAt = System.currentTimeMillis()
        refreshKeyUI()
        if (rotate) {
            broadcastPhoneChange()
        }
        showKeyDialog()
    }

    private fun broadcastPhoneChange() {
        CallMonitorService.getInstance()?.pushEngine?.broadcastKeyRotated()
    }

    private fun showKeyDialog() {
        val passphrase = prefs.userPassphrase ?: return
        val dialogBinding = DialogShowPassphraseBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.tvPassphrase.text = passphrase
        dialogBinding.ivQrCode.setImageBitmap(generateQrCode(PairingCodec.encode(passphrase, prefs.ntfyAuthToken)))
        dialogBinding.tvWarning.text = getString(R.string.security_warning_save)
        dialogBinding.btnCopyPassphrase.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SOS Ring key", passphrase))
            Toast.makeText(requireContext(), getString(R.string.security_key_copied), Toast.LENGTH_SHORT).show()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.security_your_key_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save, null)
            .show()
    }

    private fun chooseImportMethod() {
        val options = arrayOf(
            getString(R.string.security_import_paste),
            getString(R.string.security_import_qr_image),
            getString(R.string.security_import_qr_camera)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.security_import_choose)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importKeyDialog()
                    1 -> qrImageLauncher.launch("image/*")
                    2 -> qrCameraLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
                }
            }
            .show()
    }

    private fun importKeyDialog() {
        val input = TextInputEditText(requireContext()).apply { hint = getString(R.string.import_key_hint) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.security_import)
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val candidate = input.text?.toString()?.trim().orEmpty()
                importPassphrase(candidate)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun importKeyFromQrImage(uri: Uri) {
        try {
            val bitmap = requireContext().contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: throw IllegalArgumentException("Invalid image")
            importPassphrase(QrCodeBitmapDecoder.decode(bitmap))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.security_key_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun importPassphrase(candidate: String) {
        val payload = PairingCodec.decode(candidate)
        if (payload != null && PassphraseHelper.isValid(payload.passphrase)) {
            prefs.userPassphrase = payload.passphrase
            prefs.passphraseCreatedAt = System.currentTimeMillis()
            val token = payload.token
            if (!token.isNullOrBlank()) {
                prefs.ntfyAuthToken = token
                CallMonitorService.getInstance()?.pushEngine?.resubscribe()
            }
            refreshKeyUI()
            Toast.makeText(requireContext(), getString(R.string.security_key_imported), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.security_key_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun generateQrCode(content: String): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = matrix.width
        val height = matrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

}
