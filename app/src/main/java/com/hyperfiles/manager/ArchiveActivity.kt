package com.hyperfiles.manager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hyperfiles.manager.databinding.ActivityArchiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: ArchiveEntryAdapter
    private lateinit var archive: File     // original (may live in a restricted path)
    private var readable: File? = null     // directly-readable original or elevated copy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        archive = resolved
        title = archive.name
        supportActionBar?.subtitle = ArchiveEngine.detectFormat(archive).name

        adapter = ArchiveEntryAdapter { entry -> previewEntry(entry) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnExtractAll.setOnClickListener { extractAll() }
        loadEntries()
    }

    /** Resolve (and cache) a directly-readable copy; handles Android/data via Shizuku/root. */
    private suspend fun readable(): File? {
        readable?.let { return it }
        return withContext(Dispatchers.IO) { Readable.resolve(archive) }.also { readable = it }
    }

    private fun loadEntries() {
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        lifecycleScope.launch {
            val src = readable()
            if (src == null) {
                binding.progress.visibility = View.GONE
                binding.summary.text = ""
                binding.empty.text = Readable.reason()
                binding.empty.visibility = View.VISIBLE
                return@launch
            }
            val result = withContext(Dispatchers.IO) { runCatching { ArchiveEngine.list(src) } }
            binding.progress.visibility = View.GONE
            result.onSuccess { entries ->
                adapter.submit(entries.sortedWith(compareByDescending<ArchiveEngine.Entry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }))
                binding.summary.text = "${entries.size} entries"
                binding.empty.text = "This archive is empty"
                binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                binding.summary.text = ""
                binding.empty.text = "Couldn't read this archive\n${e.message ?: e.javaClass.simpleName}"
                binding.empty.visibility = View.VISIBLE
            }
        }
    }

    private fun extractAll() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val src = readable()
            if (src == null) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@ArchiveActivity, Readable.reason(), Toast.LENGTH_LONG).show()
                return@launch
            }
            val dest = ArchiveEngine.writableExtractDir(archive.parentFile ?: filesDir, ArchiveEngine.baseName(archive))
            val result = withContext(Dispatchers.IO) { runCatching { ArchiveEngine.extractAll(src, dest); dest } }
            binding.progress.visibility = View.GONE
            result.onSuccess {
                FileScanner.invalidate()
                Toast.makeText(this@ArchiveActivity, "Extracted to ${it.absolutePath}", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ArchiveActivity, BrowseActivity::class.java)
                    .putExtra(OpenHelper.EXTRA_PATH, it.absolutePath))
            }.onFailure { e ->
                Toast.makeText(this@ArchiveActivity,
                    "Extraction failed: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun previewEntry(entry: ArchiveEngine.Entry) {
        if (entry.isDirectory) return
        val base = entry.name.substringAfterLast('/').ifEmpty { "entry" }
        val outDir = File(cacheDir, "archive_preview").apply { mkdirs() }
        val outFile = File(outDir, base)
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val src = readable()
            val ok = if (src == null) false else withContext(Dispatchers.IO) {
                runCatching { ArchiveEngine.extractEntry(src, entry.name, outFile) }.getOrDefault(false)
            }
            binding.progress.visibility = View.GONE
            if (ok) OpenHelper.open(this@ArchiveActivity, outFile)
            else Toast.makeText(this@ArchiveActivity, "Could not open entry", Toast.LENGTH_SHORT).show()
        }
    }
}
