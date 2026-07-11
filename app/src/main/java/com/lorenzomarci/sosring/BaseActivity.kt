package com.lorenzomarci.sosring

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.themeResId(PrefsManager(this).themePalette))
        super.onCreate(savedInstanceState)
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    @Suppress("DEPRECATION")
    protected fun applySystemBars() {
        val background = ThemeManager.color(this, android.R.attr.colorBackground)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false

        val darkIcons = !ThemeManager.isDark(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
