package com.hyperfiles.manager

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyperfiles.manager.databinding.ActivityApkInfoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ApkInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApkInfoBinding
    private lateinit var apk: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityApkInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        apk = resolved
        title = "APK info"
        binding.btnInstall.setOnClickListener { OpenHelper.installApk(this, apk) }

        load()
    }

    private fun load() {
        binding.info.text = "Reading…"
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { buildInfo() }
            binding.info.text = text
        }
    }

    @Suppress("DEPRECATION")
    private fun buildInfo(): String {
        val pm = packageManager
        val path = apk.absolutePath
        var flags = PackageManager.GET_PERMISSIONS
        flags = flags or if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = pm.getPackageArchiveInfo(path, flags)
            ?: return "Could not parse APK (it may be a split/xapk or corrupt).\n\nPath: $path"

        val appInfo = pi.applicationInfo
        if (appInfo != null) { appInfo.sourceDir = path; appInfo.publicSourceDir = path }

        runCatching {
            appInfo?.loadIcon(pm)?.let { binding.icon.setImageDrawable(it) }
        }
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

        return sb.toString()
    }

    private fun sha256(b: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(b)
        val sb = StringBuilder()
        for (x in d) sb.append(String.format("%02X:", x.toInt() and 0xFF))
        return sb.toString().trimEnd(':')
    }
}
