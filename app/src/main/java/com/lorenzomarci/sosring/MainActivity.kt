package com.lorenzomarci.sosring

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: VipNumbersAdapter
    private val contacts = mutableListOf<VipContact>()
    private val quietRules = mutableListOf<QuietRule>()

    private val contactsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadContacts()
        }
    }

    private val runtimePermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (BuildConfig.LOCATION_ENABLED) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { handleContactPicked(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStatus()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.perm_all_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.perm_some_missing), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        setupRecyclerView()
        setupListeners()
        loadContacts()
        loadQuietRules()
        handleUpdateIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        binding.switchService.isChecked = prefs.isServiceEnabled
        binding.sliderVolume.value = prefs.volumePercent.toFloat()
        binding.tvVolumeValue.text = "${prefs.volumePercent}%"
        loadContacts()
        // Run discovery to refresh location-enabled contacts
        if (BuildConfig.LOCATION_ENABLED) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                contactsUpdatedReceiver,
                IntentFilter(NtfyService.ACTION_CONTACTS_UPDATED)
            )
            CallMonitorService.getInstance()?.ntfyService?.runDiscovery()
        }

        // Check for updates (internal flavor only)
        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            UpdateChecker(this).checkAndNotify()
        }
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.LOCATION_ENABLED) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsUpdatedReceiver)
        }
    }

    private fun setupRecyclerView() {
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> showEditDialog(position, contact) },
            onDelete = { position -> deleteContact(position) },
            onLocation = if (BuildConfig.LOCATION_ENABLED) { contact ->
                requestContactLocation(contact)
            } else null
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }

    private fun setupListeners() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkAllPermissions()) {
                    startMonitoring()
                } else {
                    binding.switchService.isChecked = false
                    Toast.makeText(this, getString(R.string.grant_perms_first), Toast.LENGTH_LONG).show()
                }
            } else {
                stopMonitoring()
            }
        }

        binding.btnRequestRuntime.setOnClickListener {
            permissionLauncher.launch(runtimePermissions)
        }

        binding.btnRequestDnd.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dnd_dialog_title))
                .setMessage(getString(R.string.dnd_dialog_msg))
                .setPositiveButton(getString(R.string.btn_open_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        binding.sliderVolume.value = prefs.volumePercent.toFloat()
        binding.tvVolumeValue.text = "${prefs.volumePercent}%"
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            prefs.volumePercent = value.toInt()
            binding.tvVolumeValue.text = "${value.toInt()}%"
        }

        binding.btnAddQuietRule.setOnClickListener {
            if (quietRules.size >= PrefsManager.MAX_QUIET_RULES) {
                Toast.makeText(this, getString(R.string.quiet_max_rules), Toast.LENGTH_SHORT).show()
            } else {
                showAddQuietRuleDialog()
            }
        }

        binding.fabAdd.setOnClickListener {
            showAddChoiceDialog()
        }

        // Location sharing settings
        if (BuildConfig.LOCATION_ENABLED) {
            binding.cardLocation.visibility = android.view.View.VISIBLE
            binding.tvLocationServer.text = getString(R.string.location_server_label, prefs.ntfyServerUrl)
            updateLocationNumberUI()

            binding.btnSaveNumber.setOnClickListener {
                if (prefs.ownPhoneNumber.isNotBlank() && !binding.etOwnNumber.isEnabled) {
                    // Currently locked — switch to edit mode
                    binding.etOwnNumber.isEnabled = true
                    binding.etOwnNumber.requestFocus()
                    binding.btnSaveNumber.text = getString(R.string.location_save)
                } else {
                    // Save mode
                    val number = binding.etOwnNumber.text.toString().trim()
                    if (number.startsWith("+") && number.length >= 10) {
                        prefs.ownPhoneNumber = number
                        Toast.makeText(this, getString(R.string.location_number_saved), Toast.LENGTH_SHORT).show()
                        updateLocationNumberUI()
                        // Restart service to connect to ntfy
                        if (prefs.isServiceEnabled) {
                            CallMonitorService.stop(this)
                            CallMonitorService.start(this)
                        }
                        checkNtfyHealth()
                    } else {
                        Toast.makeText(this, getString(R.string.location_number_invalid), Toast.LENGTH_LONG).show()
                    }
                }
            }

            binding.btnLocationLog.setOnClickListener {
                toggleLocationLog()
            }
        } else {
            binding.cardLocation.visibility = android.view.View.GONE
        }
    }

    private fun loadContacts() {
        contacts.clear()
        contacts.addAll(prefs.getContacts())
        adapter.submitList(contacts.toList())
    }

    private fun loadQuietRules() {
        quietRules.clear()
        quietRules.addAll(prefs.getQuietRules())
        refreshQuietRulesUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action == "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE") {
            val apkUrl = intent.getStringExtra("apk_url") ?: return
            val versionName = intent.getStringExtra("version_name") ?: ""
            Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
            UpdateChecker(this).downloadAndInstall(apkUrl)
        }
    }

    private fun refreshQuietRulesUI() {
        val container = binding.quietRulesContainer
        container.removeAllViews()

        quietRules.forEachIndexed { index, rule ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_quiet_rule, container, false)
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

        // Try to compress consecutive runs
        if (sorted.size >= 2) {
            val first = orderedDays.indexOf(sorted.first())
            val last = orderedDays.indexOf(sorted.last())
            if (last - first + 1 == sorted.size) {
                // Consecutive run
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
        MaterialAlertDialogBuilder(this)
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
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_quiet_rule, null)

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
                }.show(supportFragmentManager, "from_time")
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
                }.show(supportFragmentManager, "to_time")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.quiet_new_rule_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val selectedDays = chipMap.filter { it.value.isChecked }.keys
                if (selectedDays.isEmpty()) {
                    Toast.makeText(this, getString(R.string.quiet_select_day), Toast.LENGTH_SHORT).show()
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
            binding.layoutLocationStatus.visibility = android.view.View.GONE
        }
    }

    private fun checkNtfyHealth() {
        binding.layoutLocationStatus.visibility = android.view.View.VISIBLE
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
                runOnUiThread {
                    if (healthy) {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_online)
                        binding.tvLocationStatus.text = getString(R.string.location_status_ok)
                        binding.tvLocationStatus.setTextColor(getColor(R.color.status_ok))
                    } else {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                        binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                        binding.tvLocationStatus.setTextColor(getColor(R.color.status_missing))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                    binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                    binding.tvLocationStatus.setTextColor(getColor(R.color.status_missing))
                }
            }
        }.start()
    }

    private fun requestContactLocation(contact: VipContact) {
        if (prefs.ownPhoneNumber.isBlank()) {
            Toast.makeText(this, getString(R.string.location_no_number), Toast.LENGTH_SHORT).show()
            return
        }
        val ntfyService = CallMonitorService.getInstance()?.ntfyService
        if (ntfyService != null) {
            ntfyService.requestLocation(contact)
        } else {
            Toast.makeText(this, getString(R.string.grant_perms_first), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLocationLog() {
        val container = binding.locationLogContainer
        if (container.visibility == View.VISIBLE) {
            container.visibility = View.GONE
            binding.btnLocationLog.text = getString(R.string.location_log_button)
            return
        }

        container.removeAllViews()
        val logs = prefs.getLocationLogs().filter { it.type == "incoming" }
        val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

        if (logs.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.location_log_empty)
                textSize = 13f
                setTextColor(getColor(android.R.color.darker_gray))
                setPadding(0, 8, 0, 8)
            }
            container.addView(empty)
        } else {
            logs.take(50).forEach { entry ->
                val date = dateFormat.format(java.util.Date(entry.timestamp))
                val tv = TextView(this).apply {
                    text = "\u2B07 ${getString(R.string.location_log_incoming, entry.name)}\n     $date"
                    textSize = 13f
                    setPadding(0, 6, 0, 6)
                }
                container.addView(tv)
            }
        }

        container.visibility = View.VISIBLE
        binding.btnLocationLog.text = getString(R.string.location_log_hide)
    }

    private fun showAddChoiceDialog() {
        val options = arrayOf(getString(R.string.choice_from_contacts), getString(R.string.choice_manual))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_choice_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFromContacts()
                    1 -> showManualAddDialog()
                }
            }
            .show()
    }

    private fun pickFromContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            return
        }
        contactPickerLauncher.launch(null)
    }

    private fun handleContactPicked(contactUri: Uri) {
        var name = ""
        var phone = ""

        val nameCursor: Cursor? = contentResolver.query(
            contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )
        nameCursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))

                val phoneCursor: Cursor? = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId), null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        phone = pc.getString(pc.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    }
                }
            }
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_phone_found), Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(name)
        etNumber.setText(phone)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_contact_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val finalName = etName.text.toString().trim()
                val finalNumber = etNumber.text.toString().trim()
                if (finalName.isNotEmpty() && finalNumber.length > 3) {
                    contacts.add(VipContact(finalName, finalNumber))
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showManualAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etNumber.setText("+")

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_choice_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts.add(VipContact(name, number))
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showEditDialog(position: Int, contact: VipContact) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(contact.name)
        etNumber.setText(contact.number)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_contact_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts[position] = VipContact(name, number)
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun deleteContact(position: Int) {
        val contact = contacts[position]
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.remove_contact_title))
            .setMessage(getString(R.string.remove_contact_msg, contact.name, contact.number))
            .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                contacts.removeAt(position)
                saveAndRefresh()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun saveAndRefresh() {
        prefs.saveContacts(contacts)
        adapter.submitList(contacts.toList())
    }

    private fun startMonitoring() {
        prefs.isServiceEnabled = true
        CallMonitorService.start(this)
        Toast.makeText(this, getString(R.string.monitoring_on), Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        prefs.isServiceEnabled = false
        CallMonitorService.stop(this)
        Toast.makeText(this, getString(R.string.monitoring_off), Toast.LENGTH_SHORT).show()
    }

    private fun checkAllPermissions(): Boolean {
        val runtimeOk = runtimePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val dndOk = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
        return runtimeOk && dndOk
    }

    private fun updatePermissionStatus() {
        val phoneOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        val callLogOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        val dndOk = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

        val runtimeAll = phoneOk && callLogOk && notifOk

        binding.tvRuntimeStatus.text = getString(if (runtimeAll) R.string.status_granted else R.string.status_missing)
        binding.tvRuntimeStatus.setTextColor(getColor(if (runtimeAll) R.color.status_ok else R.color.status_missing))
        binding.btnRequestRuntime.isEnabled = !runtimeAll

        binding.tvDndStatus.text = getString(if (dndOk) R.string.status_granted else R.string.status_missing)
        binding.tvDndStatus.setTextColor(getColor(if (dndOk) R.color.status_ok else R.color.status_missing))
        binding.btnRequestDnd.isEnabled = !dndOk

        val allOk = runtimeAll && dndOk
        binding.switchService.isEnabled = allOk || prefs.isServiceEnabled
    }
}
