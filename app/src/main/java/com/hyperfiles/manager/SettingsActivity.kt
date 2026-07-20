package com.hyperfiles.manager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyperfiles.manager.ui.FilesDevTheme

/** First screen migrated to Jetpack Compose + Material 3 (rest of the app remains on Views). */
class SettingsActivity : AppCompatActivity() {

    private val themeKeys = listOf("system", "light", "dark", "amoled")
    private val themeLabels = listOf("System default", "Light", "Dark", "AMOLED (pure black)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        setContent { FilesDevTheme { SettingsScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen() {
        var refresh by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

        val notifLauncher = rememberLauncherForActivityResult(RequestPermission()) { refresh++ }
        val legacyLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { refresh++ }

        val filesGranted = remember(refresh) { StorageUtil.hasAllFilesAccess(this) }
        val notifOn = remember(refresh) { NotificationManagerCompat.from(this).areNotificationsEnabled() }
        val installOk = remember(refresh) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) packageManager.canRequestPackageInstalls() else true
        }

        var devTools by remember { mutableStateOf(Prefs.showDevTools(this)) }
        var liveNotify by remember { mutableStateOf(Prefs.liveNotify(this)) }
        var recycleBin by remember { mutableStateOf(Prefs.useRecycleBin(this)) }
        var themeIdx by remember { mutableIntStateOf(themeKeys.indexOf(Prefs.themeMode(this)).coerceAtLeast(0)) }
        var showThemeDialog by remember { mutableStateOf(false) }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),   // global inset padding already applied
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                SectionHeader("Permissions")
                PermissionRow("All files access", if (filesGranted) "Granted" else "Not granted",
                    !filesGranted) { grantAllFiles(legacyLauncher::launch) }
                PermissionRow("Notifications",
                    if (notifOn) "Enabled" else "Disabled (needed for background audio)", !notifOn) {
                    grantNotifications(notifLauncher::launch)
                }
                PermissionRow("Install unknown apps",
                    if (installOk) "Allowed" else "Not allowed (needed to install APKs)", !installOk) {
                    grantInstall()
                }

                SectionHeader("General")
                SwitchRow("Developer tools", "Show the Devs tab", devTools) {
                    devTools = it; Prefs.setShowDevTools(this@SettingsActivity, it)
                }
                SwitchRow("Live notifications", "Rich media controls on the status bar", liveNotify) {
                    liveNotify = it; Prefs.setLiveNotify(this@SettingsActivity, it)
                }
                SwitchRow("Recycle bin", "Move deleted files to a bin instead of erasing", recycleBin) {
                    recycleBin = it; Prefs.setUseRecycleBin(this@SettingsActivity, it)
                }
                ClickRow("Theme", themeLabels[themeIdx]) { showThemeDialog = true }

                SectionHeader("Storage")
                ClickRow("Secure folder", "Biometric / pattern protected") {
                    startActivity(Intent(this@SettingsActivity, LockActivity::class.java))
                }
                ClickRow("Recycle bin", "Restore or empty deleted files") {
                    startActivity(Intent(this@SettingsActivity, RecycleBinActivity::class.java))
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Theme") },
                confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") } },
                text = {
                    Column {
                        themeLabels.forEachIndexed { i, label ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    themeIdx = i
                                    Prefs.setThemeMode(this@SettingsActivity, themeKeys[i])
                                    Theming.applyNightMode(this@SettingsActivity)
                                    showThemeDialog = false
                                    recreate()
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = i == themeIdx, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(label)
                            }
                        }
                    }
                }
            )
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
        )
    }

    @Composable
    private fun PermissionRow(title: String, status: String, actionEnabled: Boolean, onGrant: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionEnabled) {
                FilledTonalButton(onClick = onGrant) { Text("Grant") }
            } else {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }

    @Composable
    private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    private fun grantAllFiles(legacyLaunch: (Array<String>) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyLaunch(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun grantNotifications(notifLaunch: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLaunch(android.Manifest.permission.POST_NOTIFICATIONS)
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
