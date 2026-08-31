package com.lorenzomarci.sosring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorenzomarci.sosring.databinding.ItemVipNumberBinding
import java.util.Locale

class VipNumbersAdapter(
    private val onEdit: (Int, VipContact) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onTrackTap: ((VipContact) -> Unit)? = null,
    private val onStop: ((VipContact) -> Unit)? = null,
    private val onViewPath: ((VipContact) -> Unit)? = null,
    private val onMessageAlertTap: ((VipContact, MessageApp) -> Unit)? = null,
    private val liveTrackingNumber: () -> String? = { null }
) : ListAdapter<VipContact, VipNumbersAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<VipContact>() {
        override fun areItemsTheSame(old: VipContact, new: VipContact) =
            old.number == new.number
        override fun areContentsTheSame(old: VipContact, new: VipContact) =
            old == new
    }

    inner class ViewHolder(private val binding: ItemVipNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: VipContact, position: Int) {
            binding.tvAvatar.text = contactInitials(contact.name)
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.number

            val icons = VipRowIcons.rowIcons(
                canRequest = Push.canRequestLocation(binding.root.context, contact.number),
                locationEnabled = contact.locationEnabled,
                isLiveForThisContact = liveTrackingNumber() == contact.number
            )
            val tint = binding.tvName.currentTextColor

            binding.btnGps.visibility = if (icons.showGps && onTrackTap != null) View.VISIBLE else View.GONE
            binding.btnGps.setColorFilter(tint)
            binding.btnGps.setOnClickListener { onTrackTap?.invoke(contact) }

            binding.btnStop.visibility = if (icons.showStop && onStop != null) View.VISIBLE else View.GONE
            binding.btnStop.setColorFilter(tint)
            binding.btnStop.setOnClickListener { onStop?.invoke(contact) }

            binding.btnMap.visibility = if (icons.showMap && onViewPath != null) View.VISIBLE else View.GONE
            binding.btnMap.setColorFilter(tint)
            binding.btnMap.setOnClickListener { onViewPath?.invoke(contact) }

            binding.btnMore.setColorFilter(tint)
            binding.btnMore.setOnClickListener { anchor -> showMoreMenu(anchor, position, contact) }
        }

        private fun showMoreMenu(anchor: View, position: Int, contact: VipContact) {
            val menu = PopupMenu(anchor.context, anchor)
            menu.menuInflater.inflate(R.menu.vip_row_menu, menu.menu)
            menu.menu.findItem(R.id.action_message_alerts)?.isVisible =
                VipMessageAlerts.supported && onMessageAlertTap != null
            val appItems = mapOf(
                MessageApp.WHATSAPP to R.id.action_whatsapp_alert,
                MessageApp.GOOGLE_MESSAGES to R.id.action_google_messages_alert,
                MessageApp.TELEGRAM to R.id.action_telegram_alert
            )
            appItems.forEach { (app, itemId) ->
                menu.menu.findItem(itemId)?.title = messageMenuTitle(anchor, contact, app)
            }
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> { onEdit(position, contact); true }
                    R.id.action_whatsapp_alert -> { onMessageAlertTap?.invoke(contact, MessageApp.WHATSAPP); true }
                    R.id.action_google_messages_alert -> {
                        onMessageAlertTap?.invoke(contact, MessageApp.GOOGLE_MESSAGES); true
                    }
                    R.id.action_telegram_alert -> { onMessageAlertTap?.invoke(contact, MessageApp.TELEGRAM); true }
                    R.id.action_delete -> { onDelete(position); true }
                    else -> false
                }
            }
            menu.show()
        }

        private fun messageMenuTitle(anchor: View, contact: VipContact, app: MessageApp): String {
            val appName = anchor.context.getString(
                when (app) {
                    MessageApp.WHATSAPP -> R.string.message_app_whatsapp
                    MessageApp.GOOGLE_MESSAGES -> R.string.message_app_google_messages
                    MessageApp.TELEGRAM -> R.string.message_app_telegram
                }
            )
            return when (VipMessageAlerts.state(anchor.context, contact, app)) {
                MessageAlertState.UNPAIRED -> anchor.context.getString(R.string.message_pair_app, appName)
                MessageAlertState.PAIRING -> anchor.context.getString(R.string.message_pair_cancel_app, appName)
                MessageAlertState.PAIRED -> anchor.context.getString(R.string.message_unpair_app, appName)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVipNumberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    private fun contactInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val initials = when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
            parts.size == 1 -> parts.first().take(2)
            else -> "?"
        }
        return initials.uppercase(Locale.getDefault())
    }
}
