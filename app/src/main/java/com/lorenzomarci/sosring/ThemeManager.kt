package com.lorenzomarci.sosring

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

object ThemeManager {
    fun themeResId(palette: AppPalette): Int = when (palette) {
        AppPalette.INDACO -> R.style.Theme_SOSRing_Indaco
        AppPalette.TEAL -> R.style.Theme_SOSRing_Teal
        AppPalette.ARGILLA -> R.style.Theme_SOSRing_Argilla
        AppPalette.ARDESIA -> R.style.Theme_SOSRing_Ardesia
    }

    fun applyNightMode(mode: Int) {
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun isDark(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }

    @ColorInt
    fun color(context: Context, @AttrRes attribute: Int): Int {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute 0x${attribute.toString(16)} is not defined"
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
