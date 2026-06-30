package com.lorenzomarci.sosring

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentSecurityBinding
import org.unifiedpush.android.connector.UnifiedPush

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    private val qrCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val content = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_TEXT)
        if (result.resultCode == Activity.RESULT_OK && !content.isNullOrBlank()) {
            handleScannedPeer(content.trim())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        binding.btnShowMyQr.setOnClickListener { showMyQrDialog() }
        binding.btnScanPeer.setOnClickListener {
            qrCameraLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPeerList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshStatus() {
        val registered = !UnifiedPushStore(requireContext()).endpointUrl.isNullOrBlank()
        binding.tvP2pStatus.text = getString(
            if (registered) R.string.p2p_status_registered else R.string.p2p_status_waiting
        )
        binding.btnShowMyQr.isEnabled = registered
        val noDistributor = UnifiedPush.getDistributors(requireContext()).isEmpty()
        binding.cardNoDistributor.visibility = if (noDistributor) View.VISIBLE else View.GONE
    }

    private fun showMyQrDialog() {
        val store = UnifiedPushStore(requireContext())
        val endpoint = store.endpointUrl
        val pubKey = store.pubKey
        val auth = store.auth
        if (endpoint.isNullOrBlank() || pubKey.isNullOrBlank() || auth.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.p2p_status_waiting), Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val idPubBytes = IdentityKeyStore.idPub()
            val idPub = WebPushCrypto.b64enc(idPubBytes)
            val fingerprint = MessageAuth.fingerprint(idPubBytes)
            val payload = UnifiedPushPairing.encode(PairPayload(endpoint, pubKey, auth, idPub))
            val bitmap = generateQrCode(payload)
            activity?.runOnUiThread {
                val ctx = context ?: return@runOnUiThread
                if (_binding == null) return@runOnUiThread
                val pad = (16 * resources.displayMetrics.density).toInt()
                val image = ImageView(ctx).apply {
                    setImageBitmap(bitmap)
                    setPadding(pad, pad, pad, pad)
                }
                val hint = TextView(ctx).apply {
                    text = getString(R.string.p2p_my_qr_hint)
                    setPadding(pad, 0, pad, pad)
                }
                val fingerprintLabel = TextView(ctx).apply {
                    text = getString(R.string.p2p_fingerprint_label)
                    setPadding(pad, 0, pad, 0)
                }
                val fingerprintValue = TextView(ctx).apply {
                    text = fingerprint
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(pad, 0, pad, pad)
                }
                val container = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(image)
                    addView(hint)
                    addView(fingerprintLabel)
                    addView(fingerprintValue)
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.p2p_my_qr_title)
                    .setView(container)
                    .setPositiveButton(R.string.btn_save, null)
                    .show()
            }
        }.start()
    }

    private fun handleScannedPeer(content: String) {
        val payload = UnifiedPushPairing.decode(content)
        if (payload == null || payload.idPub.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.p2p_pair_invalid), Toast.LENGTH_LONG).show()
            return
        }
        val contacts = prefs.getContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.p2p_pair_no_contacts), Toast.LENGTH_LONG).show()
            return
        }
        val names = contacts.map { "${it.name} (${it.number})" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.p2p_pair_choose_contact)
            .setItems(names) { _, which ->
                val contact = contacts[which]
                val existing = PeerStore(requireContext()).get(contact.number)
                if (existing != null && existing.idPub != payload.idPub) {
                    confirmRepair(contact, existing, payload)
                } else {
                    savePeer(contact, payload)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmRepair(contact: VipContact, existing: Peer, payload: PairPayload) {
        val oldFingerprint = fingerprintOf(existing.idPub)
        val newFingerprint = fingerprintOf(payload.idPub!!)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.p2p_repair_title)
            .setMessage(getString(R.string.p2p_repair_msg, contact.name, oldFingerprint, newFingerprint))
            .setPositiveButton(R.string.p2p_repair_confirm) { _, _ -> savePeer(contact, payload) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun savePeer(contact: VipContact, payload: PairPayload) {
        PeerStore(requireContext()).save(
            Peer(
                number = contact.number,
                endpoint = payload.endpoint,
                p256dh = payload.p256dh,
                auth = payload.auth,
                idPub = payload.idPub!!
            )
        )
        Toast.makeText(requireContext(), getString(R.string.p2p_pair_saved, contact.name), Toast.LENGTH_SHORT).show()
        refreshPeerList()
    }

    private fun refreshPeerList() {
        val container = binding.peersContainer
        container.removeAllViews()
        val peers = PeerStore(requireContext()).all()
        val contacts = prefs.getContacts()
        binding.tvPeersEmpty.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        peers.forEach { peer ->
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_peer, container, false)
            row.findViewById<TextView>(R.id.tvPeerNumber).text = peer.number
            row.findViewById<TextView>(R.id.tvPeerFingerprint).text =
                getString(R.string.p2p_peer_fingerprint, fingerprintOf(peer.idPub))
            row.findViewById<View>(R.id.btnRemovePeer).setOnClickListener { confirmRemovePeer(peer) }
            val locationEnabled = contacts.firstOrNull { it.number == peer.number }?.locationEnabled ?: false
            val locationSwitch = row.findViewById<MaterialSwitch>(R.id.swPeerLocation)
            locationSwitch.setOnCheckedChangeListener(null)
            locationSwitch.isChecked = locationEnabled
            locationSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.updateContactLocationEnabled(peer.number, isChecked)
            }
            container.addView(row)
        }
    }

    private fun confirmRemovePeer(peer: Peer) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.p2p_peer_remove_title)
            .setMessage(getString(R.string.p2p_peer_remove_msg, peer.number))
            .setPositiveButton(R.string.btn_remove) { _, _ ->
                PeerStore(requireContext()).remove(peer.number)
                refreshPeerList()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun fingerprintOf(idPubB64: String): String {
        return try {
            MessageAuth.fingerprint(WebPushCrypto.b64dec(idPubB64))
        } catch (e: Exception) {
            idPubB64
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
