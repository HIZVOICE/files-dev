package com.hyperfiles.manager

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.hyperfiles.manager.ui.FilesDevTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ApkInfoActivity : AppCompatActivity() {

    private lateinit var apk: File

    private data class ApkData(val text: String, val icon: Bitmap?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        apk = resolved
        setContent { FilesDevTheme { ApkInfoScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ApkInfoScreen() {
        val data by produceState<ApkData?>(initialValue = null) {
            value = withContext(Dispatchers.IO) { buildInfo() }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text("APK info") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val d = data
                if (d == null) {
                    Text("Reading…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        d.icon?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                        FilledTonalButton(onClick = { OpenHelper.installApk(this@ApkInfoActivity, apk) }) {
                            Text("Install")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(d.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildInfo(): ApkData {
        val pm = packageManager
        val path = apk.absolutePath
        var flags = PackageManager.GET_PERMISSIONS
        flags = flags or if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = pm.getPackageArchiveInfo(path, flags)
            ?: return ApkData("Could not parse APK (it may be a split/xapk or corrupt).\n\nPath: $path", null)

        val appInfo = pi.applicationInfo
        if (appInfo != null) { appInfo.sourceDir = path; appInfo.publicSourceDir = path }

        val icon = runCatching { appInfo?.loadIcon(pm)?.toBitmap() }.getOrNull()
        val label = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull() ?: apk.name

        val vCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        val minSdk = if (Build.VERSION.SDK_INT >= 24) appInfo?.minSdkVersion else null
        val targetSdk = appInfo?.targetSdkVersion

        val sb = StringBuilder()
        sb.append("Label: $label\n")
        sb.append("Package: ${pi.packageName}\n")
        sb.append("Version: ${pi.versionName} ($vCode)\n")
        sb.append("minSdk: ${minSdk ?: "?"}   targetSdk: ${targetSdk ?: "?"}\n")
        sb.append("Size: ${StorageUtil.formatSize(apk.length())}\n")
        sb.append("Path: $path\n")

        // Signature digests
        val digests = ArrayList<String>()
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                pi.signingInfo?.apkContentsSigners?.forEach { digests.add(sha256(it.toByteArray())) }
            } else {
                pi.signatures?.forEach { digests.add(sha256(it.toByteArray())) }
            }
        } catch (_: Exception) {}
        sb.append("\nSigning certificate SHA-256:\n")
        if (digests.isEmpty()) sb.append("(unavailable)\n") else digests.forEach { sb.append("$it\n") }

        val perms = pi.requestedPermissions
        sb.append("\nPermissions (${perms?.size ?: 0}):\n")
        if (perms.isNullOrEmpty()) sb.append("(none)\n")
        else perms.sorted().forEach { sb.append("• $it\n") }

        return ApkData(sb.toString(), icon)
    }

    private fun sha256(b: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(b)
        val sb = StringBuilder()
        for (x in d) sb.append(String.format("%02X:", x.toInt() and 0xFF))
        return sb.toString().trimEnd(':')
    }
}
