package com.hyperfiles.manager

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hyperfiles.manager.databinding.ActivitySecureFolderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SecureFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecureFolderBinding
    private lateinit var adapter: FileAdapter
    private val secureDir: File by lazy { File(filesDir, "secure").apply { mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivitySecureFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = FileAdapter(
            onClick = { OpenHelper.open(this, it) },
            onMore = { f, v -> menu(f, v) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        load()
    }

    private fun load() {
        val files = secureDir.listFiles()?.toMutableList() ?: mutableListOf()
        Sorting.sort(files, Sorting.By.NAME, asc = true)
        adapter.submit(files)
        binding.empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun menu(file: File, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Open")
        popup.menu.add("Move out")
        popup.menu.add("Delete")
        popup.setOnMenuItemClickListener {
            when (it.title) {
                "Open" -> OpenHelper.open(this, file)
                "Move out" -> moveOut(file)
                "Delete" -> {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                    load()
                }
            }
            true
        }
        popup.show()
    }

    private fun moveOut(file: File) {
        val dest = StorageUtil.downloadsDir().apply { mkdirs() }
        binding.empty.visibility = View.GONE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val out = uniqueFile(dest, file.name)
                    if (file.isDirectory) file.copyRecursively(out, false) else file.copyTo(out, false)
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                    true
                } catch (e: Exception) { false }
            }
            FileScanner.invalidate()
            Toast.makeText(this@SecureFolderActivity,
                if (ok) "Moved to Downloads" else "Failed", Toast.LENGTH_SHORT).show()
            load()
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.secure_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_change_lock -> {
                SecurityPrefs.clear(this)
                startActivity(android.content.Intent(this, LockActivity::class.java))
                finish()
            }
            R.id.action_toggle_fp -> {
                val v = !SecurityPrefs.useBiometric(this)
                SecurityPrefs.setBiometric(this, v)
                Toast.makeText(this, if (v) "Fingerprint enabled" else "Fingerprint disabled", Toast.LENGTH_SHORT).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
