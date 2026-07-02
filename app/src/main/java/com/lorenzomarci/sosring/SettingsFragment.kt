package com.lorenzomarci.sosring

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.lorenzomarci.sosring.databinding.FragmentSettingsBinding
import java.util.Calendar

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private val quietRules = mutableListOf<QuietRule>()
    private var pendingExportPassword: String? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) exportConfig(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importConfig(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        setupVolumeSlider()
        setupSoundType()
        setupQuietHours()
        setupLocationSharing()
        setupBackup()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupVolumeSlider() {
        binding.sliderVolume.value = prefs.volumePercent.toFloat()
        binding.tvVolumeValue.text = "${prefs.volumePercent}%"
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            prefs.volumePercent = value.toInt()
            binding.tvVolumeValue.text = "${value.toInt()}%"
        }
    }

    private fun setupSoundType() {
        when (prefs.overrideSoundType) {
            PrefsManager.SOUND_TYPE_NOTIFICATION -> binding.rbNotification.isChecked = true
            else -> binding.rbRingtone.isChecked = true
        }

        binding.rgSoundType.setOnCheckedChangeListener { _, checkedId ->
            prefs.overrideSoundType = when (checkedId) {
                R.id.rbNotification -> PrefsManager.SOUND_TYPE_NOTIFICATION
                else -> PrefsManager.SOUND_TYPE_RINGTONE
            }
        }
    }

    private fun setupQuietHours() {
        loadQuietRules()

        binding.btnAddQuietRule.setOnClickListener {
            if (quietRules.size >= PrefsManager.MAX_QUIET_RULES) {
                Toast.makeText(requireContext(), getString(R.string.quiet_max_rules), Toast.LENGTH_SHORT).show()
            } else {
                showAddQuietRuleDialog()
            }
        }
    }

    private fun setupLocationSharing() {
        if (!Push.supportsServerConfig) {
            binding.cardLocation.visibility = View.GONE
            return
        }
        binding.cardLocation.visibility = View.VISIBLE
        binding.tvLocationServer.text = getString(R.string.location_server_label, prefs.ntfyServerUrl)
        updateLocationNumberUI()

        binding.btnSaveNumber.setOnClickListener {
            if (prefs.ownPhoneNumber.isNotBlank() && !binding.etOwnNumber.isEnabled) {
                binding.etOwnNumber.isEnabled = true
                binding.etOwnNumber.requestFocus()
                binding.btnSaveNumber.text = getString(R.string.location_save)
            } else {
                val number = binding.etOwnNumber.text.toString().trim()
                if (number.startsWith("+") && number.length >= 10) {
                    prefs.ownPhoneNumber = number
                    Toast.makeText(requireContext(), getString(R.string.location_number_saved), Toast.LENGTH_SHORT).show()
                    updateLocationNumberUI()
                    if (prefs.isServiceEnabled) {
                        CallMonitorService.stop(requireContext())
                        CallMonitorService.start(requireContext())
                    }
                    checkNtfyHealth()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.location_number_invalid), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.etNtfyServer.setText(prefs.ntfyServerUrl)
        binding.btnSaveServer.setOnClickListener {
            val url = binding.etNtfyServer.text.toString().trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                prefs.ntfyServerUrl = url
                Toast.makeText(requireContext(), getString(R.string.location_server_saved), Toast.LENGTH_SHORT).show()
                binding.tvLocationServer.text = getString(R.string.location_server_label, url)
                checkNtfyHealth()
                if (prefs.isServiceEnabled) {
                    CallMonitorService.getInstance()?.let { svc ->
                        svc.pushEngine?.stop()
                        if (Push.canStart(requireContext())) {
                            Push.start(requireContext())
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.location_server_invalid), Toast.LENGTH_LONG).show()
            }
        }

        binding.etNtfyToken.setText(prefs.ntfyAuthToken)
        binding.btnSaveToken.setOnClickListener {
            prefs.ntfyAuthToken = binding.etNtfyToken.text.toString().trim()
            CallMonitorService.getInstance()?.pushEngine?.resubscribe()
            Toast.makeText(requireContext(), getString(R.string.location_token_saved), Toast.LENGTH_SHORT).show()
            checkNtfyHealth()
        }
    }

    private fun setupBackup() {
        if (!Push.supportsServerConfig) {
            addBackupNoPairingsNote()
        }
        binding.btnExportConfig.setOnClickListener {
            promptExportPassword { password ->
                pendingExportPassword = password
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                exportLauncher.launch("sosring-backup-$timestamp.json")
            }
        }

        binding.btnImportConfig.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(R.string.backup_import_confirm)
                .setPositiveButton(R.string.btn_continue) { _, _ ->
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun addBackupNoPairingsNote() {
        val exportButton = binding.btnExportConfig
        val parent = exportButton.parent as? LinearLayout ?: return
        val density = resources.displayMetrics.density
        val note = TextView(requireContext()).apply {
            text = getString(R.string.backup_no_pairings_note)
            textSize = 12f
            setTextColor(requireContext().getColor(R.color.status_missing))
            val bottom = (8 * density).toInt()
            setPadding(0, 0, 0, bottom)
        }
        val index = parent.indexOfChild(exportButton)
        parent.addView(note, index)
    }

    private fun exportConfig(uri: android.net.Uri) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (password.isNullOrEmpty()) return
        try {
            val config = ConfigExporter.buildConfig(
                passphrase = prefs.userPassphrase,
                passphraseCreatedAt = prefs.passphraseCreatedAt,
                ownPhoneNumber = prefs.ownPhoneNumber,
                ntfyServerUrl = prefs.ntfyServerUrl,
                ntfyAuthToken = prefs.ntfyAuthToken,
                volumePercent = prefs.volumePercent,
                overrideSoundType = prefs.overrideSoundType,
                contacts = prefs.getContacts(),
                quietRules = prefs.getQuietRules()
            )
            val envelope = ConfigCrypto.encrypt(ConfigExporter.export(config), password)
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(envelope.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(requireContext(), getString(R.string.backup_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.backup_export_failed, e.message ?: "unknown"), Toast.LENGTH_LONG).show()
        }
    }

    private fun promptExportPassword(onConfirmed: (String) -> Unit) {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val passField = TextInputEditText(ctx).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmField = TextInputEditText(ctx).apply {
            hint = getString(R.string.backup_password_confirm_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(passField)
            addView(confirmField)
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_export_confirm_title)
            .setMessage(R.string.backup_export_confirm)
            .setView(container)
            .setPositiveButton(R.string.backup_export, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val p = passField.text?.toString().orEmpty()
            val c = confirmField.text?.toString().orEmpty()
            when {
                p.isEmpty() -> Toast.makeText(ctx, R.string.backup_password_empty, Toast.LENGTH_SHORT).show()
                p != c -> Toast.makeText(ctx, R.string.backup_password_mismatch, Toast.LENGTH_SHORT).show()
                else -> {
                    dialog.dismiss()
                    onConfirmed(p)
                }
            }
        }
    }

    private fun promptImportPassword(onEntered: (String) -> Unit) {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val passField = TextInputEditText(ctx).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(passField)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.backup_import_password_title)
            .setMessage(R.string.backup_import_password_message)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ -> onEntered(passField.text?.toString().orEmpty()) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun importConfig(uri: android.net.Uri) {
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.use { isr ->
                isr.bufferedReader(Charsets.UTF_8).readText()
            } ?: return

            if (!ConfigCrypto.isEncryptedEnvelope(text)) {
                Toast.makeText(requireContext(), getString(R.string.backup_import_legacy_rejected), Toast.LENGTH_LONG).show()
                return
            }

            promptImportPassword { password ->
                Thread {
                    val plaintext = ConfigCrypto.decrypt(text, password)
                    val config = plaintext?.let { ConfigExporter.import(it) }
                    activity?.runOnUiThread {
                        if (_binding == null) return@runOnUiThread
                        when {
                            plaintext == null -> Toast.makeText(requireContext(), getString(R.string.backup_import_wrong_password), Toast.LENGTH_LONG).show()
                            config == null -> Toast.makeText(requireContext(), getString(R.string.backup_import_failed), Toast.LENGTH_LONG).show()
                            else -> applyImportedConfig(config)
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.backup_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun applyImportedConfig(config: AppConfig) {
        config.passphrase?.let { prefs.userPassphrase = it }
        if (config.passphraseCreatedAt > 0) prefs.passphraseCreatedAt = config.passphraseCreatedAt
        if (config.ownPhoneNumber.isNotBlank()) prefs.ownPhoneNumber = config.ownPhoneNumber
        if (config.ntfyServerUrl.isNotBlank()) prefs.ntfyServerUrl = config.ntfyServerUrl
        prefs.ntfyAuthToken = config.ntfyAuthToken
        prefs.volumePercent = config.volumePercent
        prefs.overrideSoundType = config.overrideSoundType
        prefs.saveContacts(config.contacts)
        prefs.saveQuietRules(config.quietRules)

        Toast.makeText(requireContext(), getString(R.string.backup_import_success), Toast.LENGTH_SHORT).show()

        if (prefs.isServiceEnabled) {
            CallMonitorService.stop(requireContext())
            CallMonitorService.start(requireContext())
        }
    }

    private fun updateLocationNumberUI() {
        val saved = prefs.ownPhoneNumber
        if (saved.isNotBlank()) {
            binding.etOwnNumber.setText(saved)
            binding.etOwnNumber.isEnabled = false
            binding.btnSaveNumber.text = getString(R.string.location_edit)
            checkNtfyHealth()
        } else {
            binding.etOwnNumber.setText("")
            binding.etOwnNumber.isEnabled = true
            binding.btnSaveNumber.text = getString(R.string.location_save)
            binding.layoutLocationStatus.visibility = View.GONE
        }
    }

    private fun checkNtfyHealth() {
        binding.layoutLocationStatus.visibility = View.VISIBLE
        binding.tvLocationStatus.text = getString(R.string.location_status_checking)
        binding.ivLocationStatus.setImageResource(android.R.drawable.ic_popup_sync)

        Thread {
            val status = Push.verifySetup(requireContext())
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                renderNtfyStatus(status)
            }
        }.start()
    }

    private fun renderNtfyStatus(status: PushSetupStatus) {
        val keyMissing = !CryptoHelper.isConfigured()
        val success = status == PushSetupStatus.CONNECTED_AUTH ||
            status == PushSetupStatus.CONNECTED_NO_AUTH

        binding.ivLocationStatus.setImageResource(
            if (success && !keyMissing) android.R.drawable.presence_online else android.R.drawable.presence_busy
        )
        binding.tvLocationStatus.setTextColor(
            requireContext().getColor(if (success && !keyMissing) R.color.status_ok else R.color.status_missing)
        )

        binding.tvLocationStatus.text = when {
            status == PushSetupStatus.CONNECTED_NO_AUTH_TOKEN_IGNORED && keyMissing -> getString(R.string.location_status_token_ignored_key_missing)
            status == PushSetupStatus.CONNECTED_NO_AUTH_TOKEN_IGNORED -> getString(R.string.location_status_token_ignored)
            status == PushSetupStatus.CONNECTED_AUTH && keyMissing -> getString(R.string.location_status_ok_auth_key_missing)
            status == PushSetupStatus.CONNECTED_NO_AUTH && keyMissing -> getString(R.string.location_status_ok_no_auth_key_missing)
            status == PushSetupStatus.CONNECTED_AUTH -> getString(R.string.location_status_ok_auth)
            status == PushSetupStatus.CONNECTED_NO_AUTH -> getString(R.string.location_status_ok_no_auth)
            status == PushSetupStatus.RATE_LIMITED -> getString(R.string.location_status_rate_limited)
            status == PushSetupStatus.TOKEN_REQUIRED -> getString(R.string.location_status_token_required)
            status == PushSetupStatus.TOKEN_REJECTED -> getString(R.string.location_status_token_rejected)
            status == PushSetupStatus.MISSING_OWN_NUMBER -> getString(R.string.location_no_number)
            status == PushSetupStatus.INVALID_URL -> getString(R.string.location_server_invalid)
            status == PushSetupStatus.SERVER_UNREACHABLE -> getString(R.string.location_status_fail)
            else -> getString(R.string.location_status_fail)
        }
    }

    private fun loadQuietRules() {
        quietRules.clear()
        quietRules.addAll(prefs.getQuietRules())
        refreshQuietRulesUI()
    }

    private fun refreshQuietRulesUI() {
        val container = binding.quietRulesContainer
        container.removeAllViews()

        quietRules.forEachIndexed { index, rule ->
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_quiet_rule, container, false)
            val tvDays = itemView.findViewById<TextView>(R.id.tvRuleDays)
            val tvTime = itemView.findViewById<TextView>(R.id.tvRuleTime)
            val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDeleteRule)

            tvDays.text = formatRuleDays(rule)
            tvTime.text = formatRuleTime(rule)
            btnDelete.setOnClickListener { deleteQuietRule(index, rule) }
            container.addView(itemView)
        }

        binding.btnAddQuietRule.isEnabled = quietRules.size < PrefsManager.MAX_QUIET_RULES
    }

    private fun formatRuleDays(rule: QuietRule): String {
        if (rule.days.size == 7) return getString(R.string.quiet_every_day)
        val dayNames = mapOf(
            Calendar.MONDAY to getString(R.string.day_mon),
            Calendar.TUESDAY to getString(R.string.day_tue),
            Calendar.WEDNESDAY to getString(R.string.day_wed),
            Calendar.THURSDAY to getString(R.string.day_thu),
            Calendar.FRIDAY to getString(R.string.day_fri),
            Calendar.SATURDAY to getString(R.string.day_sat),
            Calendar.SUNDAY to getString(R.string.day_sun)
        )
        val orderedDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val sorted = orderedDays.filter { it in rule.days }
        if (sorted.size >= 2) {
            val first = orderedDays.indexOf(sorted.first())
            val last = orderedDays.indexOf(sorted.last())
            if (last - first + 1 == sorted.size) {
                return "${dayNames[sorted.first()]}-${dayNames[sorted.last()]}"
            }
        }
        return sorted.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    private fun formatRuleTime(rule: QuietRule): String {
        val from = String.format("%02d:%02d", rule.startHour, rule.startMinute)
        val to = String.format("%02d:%02d", rule.endHour, rule.endMinute)
        val crossMidnight = rule.endHour * 60 + rule.endMinute <= rule.startHour * 60 + rule.startMinute
        return if (crossMidnight) "$from - $to ${getString(R.string.quiet_next_day)}" else "$from - $to"
    }

    private fun deleteQuietRule(index: Int, rule: QuietRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quiet_delete_title))
            .setMessage(getString(R.string.quiet_delete_msg,
                formatRuleDays(rule),
                String.format("%02d:%02d", rule.startHour, rule.startMinute),
                String.format("%02d:%02d", rule.endHour, rule.endMinute)))
            .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                quietRules.removeAt(index)
                prefs.saveQuietRules(quietRules)
                refreshQuietRulesUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddQuietRuleDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quiet_rule, null)
        val chipMap = mapOf(
            Calendar.MONDAY to view.findViewById<Chip>(R.id.chipMon),
            Calendar.TUESDAY to view.findViewById<Chip>(R.id.chipTue),
            Calendar.WEDNESDAY to view.findViewById<Chip>(R.id.chipWed),
            Calendar.THURSDAY to view.findViewById<Chip>(R.id.chipThu),
            Calendar.FRIDAY to view.findViewById<Chip>(R.id.chipFri),
            Calendar.SATURDAY to view.findViewById<Chip>(R.id.chipSat),
            Calendar.SUNDAY to view.findViewById<Chip>(R.id.chipSun)
        )
        val btnFrom = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFromTime)
        val btnTo = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToTime)
        val tvHint = view.findViewById<TextView>(R.id.tvCrossMidnightHint)
        var fromHour = 9; var fromMinute = 0
        var toHour = 18; var toMinute = 0
        btnFrom.text = String.format("%02d:%02d", fromHour, fromMinute)
        btnTo.text = String.format("%02d:%02d", toHour, toMinute)

        fun updateHint() {
            val startMin = fromHour * 60 + fromMinute
            val endMin = toHour * 60 + toMinute
            tvHint.visibility = if (endMin <= startMin) View.VISIBLE else View.GONE
        }

        btnFrom.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(fromHour).setMinute(fromMinute)
                .setTitleText(getString(R.string.quiet_from))
                .build().apply {
                    addOnPositiveButtonClickListener {
                        fromHour = hour; fromMinute = minute
                        btnFrom.text = String.format("%02d:%02d", hour, minute)
                        updateHint()
                    }
                }.show(childFragmentManager, "from_time")
        }

        btnTo.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(toHour).setMinute(toMinute)
                .setTitleText(getString(R.string.quiet_to))
                .build().apply {
                    addOnPositiveButtonClickListener {
                        toHour = hour; toMinute = minute
                        btnTo.text = String.format("%02d:%02d", hour, minute)
                        updateHint()
                    }
                }.show(childFragmentManager, "to_time")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quiet_new_rule_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val selectedDays = chipMap.filter { it.value.isChecked }.keys
            if (!QuietRuleValidator.isValid(selectedDays, fromHour, fromMinute, toHour, toMinute)) {
                Toast.makeText(requireContext(), getString(R.string.quiet_invalid_rule), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val rule = QuietRule(selectedDays, fromHour, fromMinute, toHour, toMinute)
            quietRules.add(rule)
            prefs.saveQuietRules(quietRules)
            refreshQuietRulesUI()
            dialog.dismiss()
        }
    }
}
