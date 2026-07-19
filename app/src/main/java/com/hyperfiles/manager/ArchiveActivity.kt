package com.hyperfiles.manager

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
    private lateinit var archive: File

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

    private fun loadEntries() {
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                try { ArchiveEngine.list(archive) } catch (e: Exception) { null }
            }
            binding.progress.visibility = View.GONE
            if (entries == null) {
                binding.empty.visibility = View.VISIBLE
                binding.summary.text = ""
            } else {
                adapter.submit(entries.sortedWith(compareByDescending<ArchiveEngine.Entry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }))
                binding.summary.text = "${entries.size} entries"
                binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun extractAll() {
        val destParent = archive.parentFile ?: filesDir
        val dest = uniqueDir(destParent, baseName(archive))
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { ArchiveEngine.extractAll(archive, dest); true } catch (e: Exception) { false }
            }
            binding.progress.visibility = View.GONE
            if (ok) {
                FileScanner.invalidate()
                Toast.makeText(this@ArchiveActivity, "Extracted to ${dest.name}", Toast.LENGTH_LONG).show()
                startActivity(android.content.Intent(this@ArchiveActivity, BrowseActivity::class.java)
                    .putExtra(OpenHelper.EXTRA_PATH, dest.absolutePath))
            } else {
                Toast.makeText(this@ArchiveActivity, "Extraction failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun previewEntry(entry: ArchiveEngine.Entry) {
        if (entry.isDirectory) return
        val base = entry.name.substringAfterLast('/').ifEmpty { "entry" }
        val outDir = File(cacheDir, "archive_preview")
        outDir.mkdirs()
        val outFile = File(outDir, base)
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { ArchiveEngine.extractEntry(archive, entry.name, outFile) } catch (e: Exception) { false }
            }
            binding.progress.visibility = View.GONE
            if (ok) OpenHelper.open(this@ArchiveActivity, outFile)
            else Toast.makeText(this@ArchiveActivity, "Could not open entry", Toast.LENGTH_SHORT).show()
        }
    }

    private fun baseName(f: File): String {
        val n = f.name
        val lower = n.lowercase()
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz"))
            if (lower.endsWith(s)) return n.substring(0, n.length - s.length)
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else n
    }

    private fun uniqueDir(parent: File, name: String): File {
        var f = File(parent, name)
        var i = 1
        while (f.exists()) { f = File(parent, "$name ($i)"); i++ }
        return f
    }
}
