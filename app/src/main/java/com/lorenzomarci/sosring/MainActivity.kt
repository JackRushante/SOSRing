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
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: VipNumbersAdapter
    private val contacts = mutableListOf<VipContact>()

    private val runtimePermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
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
            Toast.makeText(this, "Permessi concessi!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Alcuni permessi mancano — il servizio potrebbe non funzionare correttamente", Toast.LENGTH_LONG).show()
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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        binding.switchService.isChecked = prefs.isServiceEnabled
    }

    private fun setupRecyclerView() {
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> showEditDialog(position, contact) },
            onDelete = { position -> deleteContact(position) }
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
                    Toast.makeText(this, "Concedi prima tutti i permessi", Toast.LENGTH_LONG).show()
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
                .setTitle("Permesso Non Disturbare")
                .setMessage(
                    "Per poter disattivare la modalità \"Non disturbare\" quando arriva una chiamata VIP, " +
                    "devi autorizzare SOS Ring nelle impostazioni di sistema.\n\n" +
                    "Premi OK per aprire le impostazioni, poi cerca \"SOS Ring\" e attiva l'interruttore."
                )
                .setPositiveButton("Apri impostazioni") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        binding.fabAdd.setOnClickListener {
            showAddChoiceDialog()
        }
    }

    private fun loadContacts() {
        contacts.clear()
        contacts.addAll(prefs.getContacts())
        adapter.submitList(contacts.toList())
    }

    private fun showAddChoiceDialog() {
        val options = arrayOf("Scegli dalla rubrica", "Inserisci manualmente")
        MaterialAlertDialogBuilder(this)
            .setTitle("Aggiungi contatto VIP")
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

        // Get contact name
        val nameCursor: Cursor? = contentResolver.query(
            contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )
        nameCursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))

                // Get phone number
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
            Toast.makeText(this, "Nessun numero trovato per questo contatto", Toast.LENGTH_LONG).show()
            return
        }

        // Show confirmation dialog with pre-filled data (editable)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(name)
        etNumber.setText(phone)

        MaterialAlertDialogBuilder(this)
            .setTitle("Conferma contatto VIP")
            .setView(view)
            .setPositiveButton("Aggiungi") { _, _ ->
                val finalName = etName.text.toString().trim()
                val finalNumber = etNumber.text.toString().trim()
                if (finalName.isNotEmpty() && finalNumber.length > 3) {
                    contacts.add(VipContact(finalName, finalNumber))
                    saveAndRefresh()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showManualAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etNumber.setText("+39")

        MaterialAlertDialogBuilder(this)
            .setTitle("Aggiungi contatto VIP")
            .setView(view)
            .setPositiveButton("Aggiungi") { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts.add(VipContact(name, number))
                    saveAndRefresh()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showEditDialog(position: Int, contact: VipContact) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(contact.name)
        etNumber.setText(contact.number)

        MaterialAlertDialogBuilder(this)
            .setTitle("Modifica contatto")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts[position] = VipContact(name, number)
                    saveAndRefresh()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteContact(position: Int) {
        val contact = contacts[position]
        MaterialAlertDialogBuilder(this)
            .setTitle("Rimuovi contatto")
            .setMessage("Rimuovere ${contact.name} (${contact.number}) dalla lista VIP?")
            .setPositiveButton("Rimuovi") { _, _ ->
                contacts.removeAt(position)
                saveAndRefresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun saveAndRefresh() {
        prefs.saveContacts(contacts)
        adapter.submitList(contacts.toList())
    }

    private fun startMonitoring() {
        prefs.isServiceEnabled = true
        CallMonitorService.start(this)
        Toast.makeText(this, "Monitoraggio attivato", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        prefs.isServiceEnabled = false
        CallMonitorService.stop(this)
        Toast.makeText(this, "Monitoraggio disattivato", Toast.LENGTH_SHORT).show()
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

        binding.tvRuntimeStatus.text = if (runtimeAll) "Concessi" else "Da concedere"
        binding.tvRuntimeStatus.setTextColor(getColor(if (runtimeAll) R.color.status_ok else R.color.status_missing))
        binding.btnRequestRuntime.isEnabled = !runtimeAll

        binding.tvDndStatus.text = if (dndOk) "Concesso" else "Da concedere"
        binding.tvDndStatus.setTextColor(getColor(if (dndOk) R.color.status_ok else R.color.status_missing))
        binding.btnRequestDnd.isEnabled = !dndOk

        val allOk = runtimeAll && dndOk
        binding.switchService.isEnabled = allOk || prefs.isServiceEnabled
    }
}
