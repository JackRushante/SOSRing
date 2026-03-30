package com.lorenzomarci.sosring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.lorenzomarci.sosring.databinding.FragmentSettingsBinding
import java.util.Calendar

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private val quietRules = mutableListOf<QuietRule>()

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
        if (BuildConfig.LOCATION_ENABLED) {
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
        } else {
            binding.cardLocation.visibility = View.GONE
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
            try {
                val url = "${prefs.ntfyServerUrl}/v1/health"
                val request = okhttp3.Request.Builder().url(url).build()
                val response = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request).execute()
                val healthy = response.isSuccessful
                response.close()
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    if (healthy) {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_online)
                        binding.tvLocationStatus.text = getString(R.string.location_status_ok)
                        binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_ok))
                    } else {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                        binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                        binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_missing))
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                    binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                    binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_missing))
                }
            }
        }.start()
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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quiet_new_rule_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val selectedDays = chipMap.filter { it.value.isChecked }.keys
                if (selectedDays.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.quiet_select_day), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val rule = QuietRule(selectedDays, fromHour, fromMinute, toHour, toMinute)
                quietRules.add(rule)
                prefs.saveQuietRules(quietRules)
                refreshQuietRulesUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
