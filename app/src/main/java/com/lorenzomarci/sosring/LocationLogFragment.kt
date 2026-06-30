package com.lorenzomarci.sosring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.lorenzomarci.sosring.databinding.FragmentLocationLogBinding

class LocationLogFragment : Fragment() {

    private var _binding: FragmentLocationLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocationLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLogs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadLogs() {
        val prefs = PrefsManager(requireContext())
        val container = binding.locationLogContainer
        container.removeAllViews()

        val logs = prefs.getLocationLogs().filter { it.type == "incoming" }
        val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

        if (logs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.location_log_empty)
                textSize = 14f
                setTextColor(requireContext().getColor(android.R.color.darker_gray))
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
        } else {
            logs.take(50).forEach { entry ->
                val date = dateFormat.format(java.util.Date(entry.timestamp))
                val tv = TextView(requireContext()).apply {
                    text = "\u2B07 ${getString(R.string.location_log_incoming, entry.name)}\n     $date"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                container.addView(tv)
            }
        }
    }
}
