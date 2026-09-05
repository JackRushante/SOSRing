package com.lorenzomarci.sosring

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.ViewCallAlertSettingsBinding

object CallAlertSettingsUi {
    fun summary(context: Context, prefs: PrefsManager): String = context.getString(
        if (prefs.callAlertMode == CallAlertMode.SECOND) R.string.call_mode_summary_second else R.string.call_mode_summary_first,
        prefs.repeatCallWindowMinutes
    )

    fun show(context: Context, prefs: PrefsManager, onChanged: () -> Unit) {
        val binding = ViewCallAlertSettingsBinding.inflate(LayoutInflater.from(context))
        bind(binding.root, prefs, onChanged)
        val scroll = NestedScrollView(context).apply { addView(binding.root) }
        MaterialAlertDialogBuilder(context).setView(scroll)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    fun bind(view: View, prefs: PrefsManager, onChanged: () -> Unit = {}) {
        val b = ViewCallAlertSettingsBinding.bind(view)
        b.rgCallMode.check(if (prefs.callAlertMode == CallAlertMode.SECOND) R.id.rbCallSecond else R.id.rbCallFirst)
        b.rbCallSecond.text = view.context.getString(R.string.call_mode_second, prefs.repeatCallWindowMinutes)
        b.btnRepeatWindow.text = view.context.getString(R.string.call_window_value, prefs.repeatCallWindowMinutes)
        b.switchEscalateCalls.isChecked = prefs.escalateCallVolume
        refreshVolume(view, prefs)
        b.rgCallMode.setOnCheckedChangeListener { _, id ->
            prefs.callAlertMode = if (id == R.id.rbCallSecond) CallAlertMode.SECOND else CallAlertMode.FIRST
            refreshVolume(view, prefs)
            onChanged()
        }
        b.switchEscalateCalls.setOnCheckedChangeListener { _, enabled ->
            prefs.escalateCallVolume = enabled
            refreshVolume(view, prefs)
            onChanged()
        }
        b.btnRepeatWindow.setOnClickListener {
            val values = RepeatCallPolicy.windowOptions
            MaterialAlertDialogBuilder(view.context).setTitle(R.string.call_window_title)
                .setSingleChoiceItems(values.map { view.context.getString(R.string.call_window_value, it) }.toTypedArray(),
                    values.indexOf(prefs.repeatCallWindowMinutes)) { dialog, index ->
                    prefs.repeatCallWindowMinutes = values[index]
                    b.btnRepeatWindow.text = view.context.getString(R.string.call_window_value, values[index])
                    b.rbCallSecond.text = view.context.getString(R.string.call_mode_second, values[index])
                    onChanged()
                    dialog.dismiss()
                }.setNegativeButton(R.string.btn_cancel, null).show()
        }
    }

    fun refreshVolume(view: View, prefs: PrefsManager) {
        val b = ViewCallAlertSettingsBinding.bind(view)
        val base = prefs.volumePercent
        b.tvEscalatePreview.text = if (base >= 50) view.context.getString(R.string.call_escalate_high_volume)
            else view.context.getString(R.string.call_escalate_preview, base, (base * 2).coerceAtMost(100))
    }
}
