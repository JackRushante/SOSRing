package com.lorenzomarci.sosring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorenzomarci.sosring.databinding.ItemVipNumberBinding

class VipNumbersAdapter(
    private val onEdit: (Int, VipContact) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onLocation: ((VipContact) -> Unit)? = null,
    private val onRingtoneToggle: ((Int, VipContact) -> Unit)? = null
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
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.number
            binding.btnEdit.setOnClickListener { onEdit(position, contact) }
            binding.btnDelete.setOnClickListener { onDelete(position) }

            // Ringtone toggle
            updateRingtoneIcon(contact.ringtoneEnabled)
            binding.btnRingtone.setOnClickListener {
                onRingtoneToggle?.invoke(position, contact)
            }

            // Location button
            if (BuildConfig.LOCATION_ENABLED && contact.locationEnabled && onLocation != null) {
                binding.btnLocation.visibility = View.VISIBLE
                binding.btnLocation.setOnClickListener { onLocation.invoke(contact) }
            } else {
                binding.btnLocation.visibility = View.GONE
            }
        }

        private fun updateRingtoneIcon(enabled: Boolean) {
            binding.btnRingtone.setImageResource(
                if (enabled) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode
            )
            // Tint to match text color so it's visible in both light and dark mode
            val color = binding.tvName.currentTextColor
            binding.btnRingtone.setColorFilter(color)
            binding.btnRingtone.alpha = if (enabled) 1.0f else 0.4f
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
}
