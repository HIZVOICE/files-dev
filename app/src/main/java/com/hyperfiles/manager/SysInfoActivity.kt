package com.hyperfiles.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyperfiles.manager.databinding.ActivitySysinfoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SysInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySysinfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivitySysinfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "System info"

        binding.btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("sysinfo", binding.sysContent.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

        load()
    }

    private fun load() {
        binding.sysContent.text = "Reading…"
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { build() }
            binding.sysContent.text = text
        }
    }

    private fun build(): String {
        val sb = StringBuilder()
        sb.append("── Device ──\n")
        sb.append("Manufacturer: ${Build.MANUFACTURER}\n")
        sb.append("Brand:        ${Build.BRAND}\n")
        sb.append("Model:        ${Build.MODEL}\n")
        sb.append("Device:       ${Build.DEVICE}\n")
        sb.append("Product:      ${Build.PRODUCT}\n")
        sb.append("Board:        ${Build.BOARD}\n")
        sb.append("Hardware:     ${Build.HARDWARE}\n")
        sb.append("Bootloader:   ${Build.BOOTLOADER}\n")
        sb.append("\n── Android ──\n")
        sb.append("Release:      ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        if (Build.VERSION.SDK_INT >= 23) sb.append("Security patch: ${Build.VERSION.SECURITY_PATCH}\n")
        sb.append("Build ID:     ${Build.ID}\n")
        sb.append("Incremental:  ${Build.VERSION.INCREMENTAL}\n")
        sb.append("Type/Tags:    ${Build.TYPE} / ${Build.TAGS}\n")
        sb.append("Fingerprint:\n${Build.FINGERPRINT}\n")
        sb.append("ABIs:         ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
        sb.append("Kernel:       ${System.getProperty("os.version")}\n")
        sb.append("Root:         ${if (RootShell.isRootAvailable()) "available" else "not detected"}\n")

        val kernel = RootShell.sh("cat /proc/version").stdout.trim()
        if (kernel.isNotEmpty()) sb.append("\n── /proc/version ──\n$kernel\n")

        val props = RootShell.sh("getprop").stdout.trim()
        sb.append("\n── getprop (${props.lines().size} lines) ──\n")
        sb.append(if (props.isEmpty()) "(unavailable)\n" else props)
        return sb.toString()
    }
}
