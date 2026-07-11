package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lorenzomarci.sosring.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var toggle: ActionBarDrawerToggle

    private val contactsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is HomeFragment) {
                current.onResume()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_home, R.string.nav_home
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), getString(R.string.nav_home))
                R.id.nav_settings -> loadFragment(SettingsFragment(), getString(R.string.nav_settings))
                R.id.nav_location_log -> loadFragment(LocationLogFragment(), getString(R.string.nav_location_log))
                R.id.nav_privacy -> loadFragment(PrivacyFragment(), getString(R.string.nav_privacy))
                R.id.nav_security -> loadFragment(SecurityFragment(), getString(R.string.security_title))
            }
            menuItem.isChecked = true
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), getString(R.string.nav_home))
            binding.navigationView.setCheckedItem(R.id.nav_home)
        }

        handleUpdateIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            contactsUpdatedReceiver,
            IntentFilter(Push.ACTION_CONTACTS_UPDATED)
        )
        CallMonitorService.getInstance()?.pushEngine?.runDiscovery()
        Push.ensureRegistered(this)
        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            UpdateChecker(this).checkAndNotify()
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsUpdatedReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action == "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE") {
            if (!isSelfOriginatedUpdateIntent(intent)) {
                Log.w("MainActivity", "Ignoring update intent from foreign source")
                return
            }
            val apkUrl = intent.getStringExtra("apk_url") ?: return
            Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
            UpdateChecker(this).downloadAndInstall(apkUrl)
        }
    }

    private fun isSelfOriginatedUpdateIntent(intent: Intent): Boolean {
        if (intent.component?.packageName != packageName) return false
        val token = intent.getStringExtra("update_token")
        val expectedToken = prefs.pendingUpdateToken
        if (token.isNullOrBlank() || expectedToken.isNullOrBlank() || token != expectedToken) return false
        prefs.pendingUpdateToken = null
        return true
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current !is HomeFragment) {
                loadFragment(HomeFragment(), getString(R.string.nav_home))
                binding.navigationView.setCheckedItem(R.id.nav_home)
            } else {
                super.onBackPressed()
            }
        }
    }
}
