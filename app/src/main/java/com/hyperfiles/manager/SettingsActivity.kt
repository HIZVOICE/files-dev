package com.hyperfiles.manager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.hyperfiles.manager.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val notifPerm =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
            updateStatuses()
        }
    private val legacyStorage =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatuses()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.permAllFilesBtn.setOnClickListener { grantAllFiles() }
        binding.permNotifBtn.setOnClickListener { grantNotifications() }
        binding.permInstallBtn.setOnClickListener { grantInstall() }

        binding.switchDevTools.isChecked = Prefs.showDevTools(this)
        binding.switchDevTools.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowDevTools(this, checked)
        }

        binding.switchLiveNotify.isChecked = Prefs.liveNotify(this)
        binding.switchLiveNotify.setOnCheckedChangeListener { _, checked ->
            Prefs.setLiveNotify(this, checked)
        }

        binding.switchRecycleBin.isChecked = Prefs.useRecycleBin(this)
        binding.switchRecycleBin.setOnCheckedChangeListener { _, checked ->
            Prefs.setUseRecycleBin(this, checked)
        }

        binding.themeRow.setOnClickListener { Anim.bounce(it); themeDialog() }
        binding.secureFolderRow.setOnClickListener { v -> Anim.bounce(v); v.postDelayed({ startActivity(Intent(this, LockActivity::class.java)) }, 90) }
        binding.recycleBinRow.setOnClickListener { v -> Anim.bounce(v); v.postDelayed({ startActivity(Intent(this, RecycleBinActivity::class.java)) }, 90) }
        updateThemeSummary()
    }

    private val themeKeys = listOf("system", "light", "dark", "amoled")
    private val themeLabels = arrayOf("System default", "Light", "Dark", "AMOLED (pure black)")

    private fun updateThemeSummary() {
        val idx = themeKeys.indexOf(Prefs.themeMode(this)).coerceAtLeast(0)
        binding.themeSummary.text = themeLabels[idx]
    }

    private fun themeDialog() {
        val current = themeKeys.indexOf(Prefs.themeMode(this)).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(themeLabels, current) { d, which ->
                Prefs.setThemeMode(this, themeKeys[which])
                Theming.applyNightMode(this)
                d.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        val files = StorageUtil.hasAllFilesAccess(this)
        binding.permAllFilesStatus.text = if (files) "Granted" else "Not granted"
        binding.permAllFilesBtn.isEnabled = !files

        val notif = NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.permNotifStatus.text = if (notif) "Enabled" else "Disabled (needed for background audio)"
        binding.permNotifBtn.isEnabled = !notif

        val install = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            packageManager.canRequestPackageInstalls() else true
        binding.permInstallStatus.text = if (install) "Allowed" else "Not allowed (needed to install APKs)"
        binding.permInstallBtn.isEnabled = !install
    }

    private fun grantAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyStorage.launch(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun grantInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            }
        }
    }
}
