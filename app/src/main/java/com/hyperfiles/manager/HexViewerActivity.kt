package com.hyperfiles.manager

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyperfiles.manager.databinding.ActivityHexViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HexViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHexViewerBinding
    private val maxDump = 256 * 1024 // 256 KB shown as hex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityHexViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        title = file.name

        binding.banner.visibility = View.GONE
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val readFile = when {
                        file.canRead() -> file
                        RootShell.isRootAvailable() -> RootShell.rootCopyToCache(file, cacheDir) ?: file
                        else -> file
                    }
                    val out = java.io.ByteArrayOutputStream()
                    readFile.inputStream().use { ins ->
                        val buf = ByteArray(8192)
                        var total = 0
                        while (total < maxDump) {
                            val r = ins.read(buf, 0, minOf(buf.size, maxDump - total))
                            if (r < 0) break
                            out.write(buf, 0, r); total += r
                        }
                    }
                    out.toByteArray()
                } catch (e: Exception) { ByteArray(0) }
            }

            val detected = Magic.detect(bytes)
            setupBanner(file, detected)

            supportActionBar?.subtitle =
                "${detected.label} · ${StorageUtil.formatSize(file.length())}"

            val dump = withContext(Dispatchers.Default) { Magic.hexDump(bytes, maxDump) }
            binding.hexContent.text = if (dump.isEmpty()) "(empty or unreadable)" else dump
            if (file.length() > maxDump) {
                binding.hexContent.append("\n… [showing first 256 KB of ${StorageUtil.formatSize(file.length())}]")
            }
        }
    }

    private fun setupBanner(file: File, d: Magic.Detected) {
        val (text, action) = when (d.kind) {
            Magic.Kind.IMAGE -> "Detected ${d.label} — open as image" to Runnable {
                startActivity(android.content.Intent(this, ImageViewerActivity::class.java)
                    .putExtra(OpenHelper.EXTRA_PATH, file.absolutePath))
            }
            Magic.Kind.VIDEO, Magic.Kind.AUDIO -> "Detected ${d.label} — play in player" to Runnable {
                OpenHelper.launchPlayer(this, file)
            }
            Magic.Kind.ARCHIVE -> "Detected ${d.label} — open archive" to Runnable {
                OpenHelper.launchArchive(this, file)
            }
            Magic.Kind.TEXT -> "Detected ${d.label} — open as text" to Runnable {
                startActivity(android.content.Intent(this, TextViewerActivity::class.java)
                    .putExtra(OpenHelper.EXTRA_PATH, file.absolutePath))
            }
            else -> return
        }
        binding.banner.visibility = View.VISIBLE
        binding.bannerText.text = text
        binding.bannerAction.setOnClickListener { action.run() }
    }
}
