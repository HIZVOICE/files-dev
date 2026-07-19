package com.hyperfiles.manager

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyperfiles.manager.databinding.ActivityTextViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TextViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextViewerBinding
    private lateinit var file: File
    private val maxBytes = 3 * 1024 * 1024
    private var truncated = false
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityTextViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        file = resolved
        title = file.name
        setEditing(false)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val readFile = when {
                        file.canRead() -> file
                        RootShell.isRootAvailable() -> RootShell.rootCopyToCache(file, cacheDir) ?: file
                        else -> file
                    }
                    val out = java.io.ByteArrayOutputStream()
                    readFile.inputStream().use { ins ->
                        val buf = ByteArray(8192); var total = 0
                        while (total < maxBytes) {
                            val r = ins.read(buf, 0, minOf(buf.size, maxBytes - total))
                            if (r < 0) break
                            out.write(buf, 0, r); total += r
                        }
                    }
                    truncated = file.length() > maxBytes
                    String(out.toByteArray()) + if (truncated) "\n\n… [truncated at 3 MB — saving disabled]" else ""
                } catch (e: Exception) { "Could not read file:\n${e.message}" }
            }
            binding.textContent.setText(text)
            supportActionBar?.subtitle = StorageUtil.permString(file)
        }
    }

    private fun setEditing(b: Boolean) {
        editing = b
        if (b) {
            binding.textContent.setTextIsSelectable(false)
            binding.textContent.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            binding.textContent.isFocusableInTouchMode = true
            binding.textContent.isFocusable = true
            binding.textContent.requestFocus()
        } else {
            binding.textContent.setTextIsSelectable(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text_menu, menu)
        menu.findItem(R.id.action_edit)?.isChecked = editing
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> {
                setEditing(!editing)
                item.isChecked = editing
                Toast.makeText(this, if (editing) "Editing enabled" else "Read-only", Toast.LENGTH_SHORT).show()
            }
            R.id.action_save -> save()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() {
        if (truncated) {
            Toast.makeText(this, "File was truncated — saving disabled to avoid data loss", Toast.LENGTH_LONG).show()
            return
        }
        val content = binding.textContent.text.toString()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (file.canWrite()) { file.writeText(content); "Saved" }
                    else if (RootShell.isRootAvailable()) {
                        val tmp = File(cacheDir, "edit_${file.name}")
                        tmp.writeText(content)
                        if (RootShell.rootWriteFrom(tmp, file)) "Saved (root)" else "Save failed"
                    } else "Read-only: no permission"
                } catch (e: Exception) { "Save failed: ${e.message}" }
            }
            if (result.startsWith("Saved")) FileScanner.invalidate()
            Toast.makeText(this@TextViewerActivity, result, Toast.LENGTH_SHORT).show()
        }
    }
}
