package com.hyperfiles.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperfiles.manager.ui.FilesDevTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SysInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        setContent { FilesDevTheme { SysInfoScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SysInfoScreen() {
        val text by produceState(initialValue = "Reading…") {
            value = withContext(Dispatchers.IO) { build() }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text("System info") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("sysinfo", text))
                            Toast.makeText(this@SysInfoActivity, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy") }
                    }
                )
            }
        ) { pad ->
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
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
