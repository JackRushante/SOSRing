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
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: VipNumbersAdapter
    private val contacts = mutableListOf<VipContact>()

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateMuteTimerUI()
            if (prefs.isMuted) {
                countdownHandler.postDelayed(this, 60_000)
            }
        }
    }

    private val liveRefreshHandler = Handler(Looper.getMainLooper())
    private val liveRefreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null && ::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
                liveRefreshHandler.postDelayed(this, 10_000)
            }
        }
    }

    private val runtimePermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
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
            Toast.makeText(requireContext(), getString(R.string.perm_all_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.perm_some_missing), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        setupRecyclerView()
        setupListeners()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        binding.switchService.isChecked = prefs.isServiceEnabled
        loadContacts()
        updateMuteTimerUI()
        if (prefs.isMuted) {
            countdownHandler.post(countdownRunnable)
        }
        liveRefreshHandler.post(liveRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
        liveRefreshHandler.removeCallbacks(liveRefreshRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownHandler.removeCallbacks(countdownRunnable)
        liveRefreshHandler.removeCallbacks(liveRefreshRunnable)
        _binding = null
    }

private fun setupRecyclerView() {
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> showEditDialog(position, contact) },
            onDelete = { position -> deleteContact(position) },
            onTrackTap = { contact -> showTrackChoiceDialog(contact) },
            onStop = { contact -> stopLiveTrackingFor(contact) },
            onViewPath = { contact -> viewContactPath(contact) },
            liveTrackingNumber = {
                CallMonitorService.getInstance()?.pushEngine?.liveContactNumber
            }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter
    }

    private fun showTrackChoiceDialog(contact: VipContact) {
        if (!Push.supportsLiveTracking) {
            requestContactLocation(contact)
            return
        }
        val options = arrayOf(
            getString(R.string.track_choice_single),
            getString(R.string.track_choice_follow)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.track_choice_title, contact.name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestContactLocation(contact)
                    1 -> startLiveFollow(contact)
                }
            }
            .show()
    }

    private fun startLiveFollow(contact: VipContact) {
        val ntfy = CallMonitorService.getInstance()?.pushEngine
        if (ntfy == null) {
            if (Push.supportsServerConfig) {
                showLocationShareUnavailable()
            } else {
                Toast.makeText(requireContext(), getString(R.string.location_service_not_running), Toast.LENGTH_LONG).show()
            }
            return
        }
        val activeNumber = ntfy.liveContactNumber
        if (activeNumber != null && activeNumber != contact.number) {
            Toast.makeText(requireContext(), getString(R.string.live_track_already_active), Toast.LENGTH_LONG).show()
            return
        }
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_live_track_duration, null)
        val npDuration = view.findViewById<NumberPicker>(R.id.npDuration)
        npDuration.minValue = LiveTrackingUiPolicy.MIN_DURATION_MINUTES
        npDuration.maxValue = LiveTrackingUiPolicy.MAX_DURATION_MINUTES
        npDuration.value = LiveTrackingUiPolicy.DEFAULT_DURATION_MINUTES
        npDuration.wrapSelectorWheel = false
        view.findViewById<android.widget.TextView>(R.id.tvDurationLabel).text =
            getString(R.string.live_track_duration_label)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.live_track_dialog_title))
            .setView(view)
            .setMessage(getString(R.string.live_track_dialog_message, npDuration.value))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = npDuration.value
                if (ntfy.startLiveTracking(contact, minutes)) {
                    Toast.makeText(ctx, getString(R.string.live_track_started, minutes), Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(ctx, getString(R.string.live_track_already_active), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun stopLiveTrackingFor(contact: VipContact) {
        val ntfy = CallMonitorService.getInstance()?.pushEngine ?: return
        if (ntfy.liveContactNumber == contact.number) {
            ntfy.stopLiveTracking()
            Toast.makeText(requireContext(), getString(R.string.location_live_stopped), Toast.LENGTH_SHORT).show()
            adapter.notifyDataSetChanged()
        }
    }

    private fun viewContactPath(contact: VipContact) {
        val ntfy = CallMonitorService.getInstance()?.pushEngine
        val activeSession = ntfy?.getLiveSessionId()
        if (activeSession == null || ntfy.liveContactNumber != contact.number) {
            Toast.makeText(requireContext(), getString(R.string.live_track_no_session), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(LiveMapActivity.intent(requireContext(), activeSession, contact.name, true))
    }

    private fun setupListeners() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkAllPermissions()) {
                    startMonitoring()
                } else {
                    binding.switchService.isChecked = false
                    Toast.makeText(requireContext(), getString(R.string.grant_perms_first), Toast.LENGTH_LONG).show()
                }
            } else {
                stopMonitoring()
            }
        }

        binding.btnRequestRuntime.setOnClickListener {
            if (hasForegroundLocationPermission() && !hasBackgroundLocationPermission()) {
                Toast.makeText(requireContext(), getString(R.string.location_bg_perm_needed), Toast.LENGTH_LONG).show()
                openAppSettings()
            } else {
                permissionLauncher.launch(runtimePermissions)
            }
        }

        binding.btnRequestDnd.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dnd_dialog_title))
                .setMessage(getString(R.string.dnd_dialog_msg))
                .setPositiveButton(getString(R.string.btn_open_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        binding.fabAdd.setOnClickListener {
            showAddChoiceDialog()
        }

        binding.btnMuteTimer.setOnClickListener {
            if (prefs.isMuted) {
                prefs.muteUntilTimestamp = 0L
                BootReceiver.cancelMuteAlarm(requireContext())
                Toast.makeText(requireContext(), getString(R.string.mute_cancelled), Toast.LENGTH_SHORT).show()
                updateMuteTimerUI()
                countdownHandler.removeCallbacks(countdownRunnable)
            } else {
                showMuteTimerDialog()
            }
        }
    }

    private fun showMuteTimerDialog() {
        val picker = NumberPicker(requireContext()).apply {
            minValue = 1
            maxValue = 12
            value = 1
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mute_dialog_title))
            .setMessage(getString(R.string.mute_dialog_message))
            .setView(picker)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val hours = picker.value
                val muteUntil = System.currentTimeMillis() + hours * 3600_000L
                prefs.muteUntilTimestamp = muteUntil
                BootReceiver.scheduleMuteAlarm(requireContext(), muteUntil)
                Toast.makeText(requireContext(), getString(R.string.mute_activated, hours), Toast.LENGTH_SHORT).show()
                updateMuteTimerUI()
                countdownHandler.post(countdownRunnable)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun updateMuteTimerUI() {
        if (prefs.isMuted) {
            val remaining = prefs.muteUntilTimestamp - System.currentTimeMillis()
            val hours = (remaining / 3600_000).toInt()
            val minutes = ((remaining % 3600_000) / 60_000).toInt()
            binding.tvMuteCountdown.text = getString(R.string.mute_countdown, hours, minutes)
            binding.tvMuteCountdown.visibility = View.VISIBLE
            binding.btnMuteTimer.text = getString(R.string.mute_button_cancel)
        } else {
            binding.tvMuteCountdown.visibility = View.GONE
            binding.btnMuteTimer.text = getString(R.string.mute_button)
        }
    }

    private fun loadContacts() {
        contacts.clear()
        contacts.addAll(prefs.getContacts())
        adapter.submitList(contacts.toList())
    }

    private fun requestContactLocation(contact: VipContact) {
        val block = Push.locationBlock(requireContext(), contact)
        if (block != null) {
            Toast.makeText(requireContext(), block, Toast.LENGTH_LONG).show()
            return
        }
        if (!Push.requestLocation(requireContext(), contact)) {
            Toast.makeText(requireContext(), getString(R.string.location_service_not_running), Toast.LENGTH_LONG).show()
        }
    }

    private fun locationShareBlockReason(): LocationShareBlockReason {
        return LocationShareReadiness.check(
            ownPhoneNumber = prefs.ownPhoneNumber,
            serverUrl = prefs.ntfyServerUrl,
            encryptionConfigured = CryptoHelper.isConfigured()
        )
    }

    private fun showLocationShareUnavailable() {
        val blocker = locationShareBlockReason()
        if (blocker != LocationShareBlockReason.NONE) {
            Toast.makeText(requireContext(), locationShareBlockMessage(blocker), Toast.LENGTH_LONG).show()
            return
        }

        Thread {
            val status = Push.verifySetup(requireContext())
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                Toast.makeText(
                    requireContext(),
                    liveUnavailableMessageFor(status),
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun liveUnavailableMessageFor(status: PushSetupStatus): String {
        return when (status) {
            PushSetupStatus.TOKEN_REQUIRED -> getString(R.string.location_status_token_required)
            PushSetupStatus.TOKEN_REJECTED -> getString(R.string.location_status_token_rejected)
            PushSetupStatus.INVALID_URL -> getString(R.string.location_server_invalid)
            PushSetupStatus.MISSING_OWN_NUMBER -> getString(R.string.location_no_number)
            PushSetupStatus.SERVER_UNREACHABLE -> getString(R.string.location_status_fail)
            PushSetupStatus.CONNECTED_NO_AUTH_TOKEN_IGNORED -> getString(R.string.location_status_token_ignored)
            PushSetupStatus.RATE_LIMITED -> getString(R.string.location_status_rate_limited)
            else -> getString(R.string.grant_perms_first)
        }
    }

    private fun locationShareBlockMessage(reason: LocationShareBlockReason): String {
        return when (reason) {
            LocationShareBlockReason.MISSING_OWN_NUMBER -> getString(R.string.location_no_number)
            LocationShareBlockReason.MISSING_SERVER -> getString(R.string.location_server_missing)
            LocationShareBlockReason.MISSING_KEY -> getString(R.string.location_key_missing)
            LocationShareBlockReason.NONE -> getString(R.string.location_status_ok)
        }
    }

    private fun showAddChoiceDialog() {
        val options = arrayOf(getString(R.string.choice_from_contacts), getString(R.string.choice_manual))
        MaterialAlertDialogBuilder(requireContext())
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            return
        }
        contactPickerLauncher.launch(null)
    }

    private fun handleContactPicked(contactUri: Uri) {
        var name = ""
        var phone = ""
        val ctx = requireContext()

        val nameCursor: Cursor? = ctx.contentResolver.query(
            contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )
        nameCursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val phoneCursor: Cursor? = ctx.contentResolver.query(
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
            Toast.makeText(ctx, getString(R.string.no_phone_found), Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(name)
        etNumber.setText(phone)

        MaterialAlertDialogBuilder(ctx)
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
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etNumber.setText("+")

        MaterialAlertDialogBuilder(requireContext())
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
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(contact.name)
        etNumber.setText(contact.number)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_contact_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts[position] = contact.copy(name = name, number = number)
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun deleteContact(position: Int) {
        val contact = contacts[position]
        MaterialAlertDialogBuilder(requireContext())
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
        CallMonitorService.getInstance()?.refreshNotification()
    }

    private fun startMonitoring() {
        prefs.isServiceEnabled = true
        CallMonitorService.start(requireContext())
        Toast.makeText(requireContext(), getString(R.string.monitoring_on), Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        prefs.isServiceEnabled = false
        CallMonitorService.stop(requireContext())
        Toast.makeText(requireContext(), getString(R.string.monitoring_off), Toast.LENGTH_SHORT).show()
    }

    private fun checkAllPermissions(): Boolean {
        val ctx = requireContext()
        val phoneOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val callLogOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        val contactsOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val runtimeOk = phoneOk && callLogOk && contactsOk && notifOk &&
            hasForegroundLocationPermission() && hasBackgroundLocationPermission()
        val dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
        return runtimeOk && dndOk
    }

    private fun updatePermissionStatus() {
        val ctx = requireContext()
        val phoneOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        val callLogOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        val contactsOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        val corePermsOk = phoneOk && callLogOk && contactsOk && notifOk && hasForegroundLocationPermission()
        val bgLocationOk = hasBackgroundLocationPermission()
        val dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

        val runtimeAll = corePermsOk && bgLocationOk

        binding.tvRuntimeStatus.text = getString(if (corePermsOk) R.string.status_granted else R.string.status_missing)
        binding.tvRuntimeStatus.setTextColor(ctx.getColor(if (corePermsOk) R.color.status_ok else R.color.status_missing))
        binding.btnRequestRuntime.isEnabled = !runtimeAll

        binding.tvDndStatus.text = getString(if (dndOk) R.string.status_granted else R.string.status_missing)
        binding.tvDndStatus.setTextColor(ctx.getColor(if (dndOk) R.color.status_ok else R.color.status_missing))
        binding.btnRequestDnd.isEnabled = !dndOk

        val allOk = runtimeAll && dndOk
        binding.switchService.isEnabled = allOk || prefs.isServiceEnabled
        updateWarningBanner()
    }

    private fun updateWarningBanner() {
        val ctx = requireContext()
        val callLogOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val phoneStateOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        val criticalMissing = PermissionHealth.criticalMissing(callLogOk, phoneStateOk, dndOk).isNotEmpty()
        val autoRevokeActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !ctx.packageManager.isAutoRevokeWhitelisted
        } else false
        when (HomeWarningPolicy.decide(prefs.isServiceEnabled, criticalMissing, autoRevokeActive)) {
            HomeWarning.PERMISSIONS_REVOKED -> {
                binding.tvWarning.text = getString(R.string.home_warn_perms_revoked)
                binding.warningBanner.visibility = View.VISIBLE
            }
            HomeWarning.AUTO_REVOKE -> {
                binding.tvWarning.text = getString(R.string.home_warn_auto_revoke)
                binding.warningBanner.visibility = View.VISIBLE
            }
            HomeWarning.NONE -> binding.warningBanner.visibility = View.GONE
        }
        binding.btnWarningAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", ctx.packageName, null)))
        }
    }

    private fun hasForegroundLocationPermission(): Boolean {
        val ctx = requireContext()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun openAppSettings() {
        val uri = Uri.parse("package:${requireContext().packageName}")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }
}
