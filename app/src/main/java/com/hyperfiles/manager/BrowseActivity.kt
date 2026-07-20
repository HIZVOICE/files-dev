package com.hyperfiles.manager

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hyperfiles.manager.databinding.ActivityBrowseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BrowseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SEARCH = "extra_search"
    }

    private lateinit var binding: ActivityBrowseBinding
    private lateinit var adapter: FileAdapter

    private lateinit var startDir: File
    private lateinit var currentDir: File
    private var fullList: List<File> = emptyList()
    private var query: String = ""

    private var sortBy = Sorting.By.NAME
    private var asc = true
    private var grid = false
    private var openSearch = false
    private var showHidden = false
    private val rootDirPaths = HashSet<String>()
    private val elevSizes = HashMap<String, Long>()
    // Matches a toybox `ls -lA` row: perms(+optional SELinux flag) links owner group size date time name
    private val lsLine = Regex("""^([bcdlps-][rwxsStT-]{9})\S*\s+\d+\s+\S+\s+\S+\s+(\d+)\s+\S+\s+\S+\s+(.+)$""")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(OpenHelper.EXTRA_PATH) ?: StorageUtil.primaryStorage().absolutePath
        openSearch = intent.getBooleanExtra(EXTRA_SEARCH, false)
        startDir = File(path)
        currentDir = startDir
        if (SafHelper.isRestricted(currentDir) && !Elevated.available()) {
            SafHelper.openForRestricted(this, currentDir); finish(); return
        }

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            if (adapter.selectionMode) exitSelectionUi() else goUp()
        }

        adapter = FileAdapter(
            onClick = { onItem(it) },
            onMore = { f, v -> FileOps.showMenu(this, f, v) { load() } },
            selectable = true
        )
        adapter.onEnterSelectionMode = {
            binding.toolbar.setNavigationIcon(R.drawable.ic_close)
            invalidateOptionsMenu()
        }
        adapter.onSelectionChanged = { count -> onSelectionCount(count) }
        adapter.isDirOverride = { rootDirPaths.contains(it.absolutePath) }
        adapter.sizeOverride = { elevSizes[it.absolutePath] }
        applyLayoutManager()
        binding.list.adapter = adapter
        Bounce.attach(binding.list)

        binding.swipe.setOnRefreshListener { FileScanner.invalidate(); load() }
        binding.pasteBar.setOnClickListener {
            FileOps.paste(this, currentDir, lifecycleScope) { load() }
            binding.pasteBar.visibility = android.view.View.GONE
        }

        load()
    }

    private fun applyLayoutManager() {
        binding.list.layoutManager =
            if (grid) GridLayoutManager(this, 3) else LinearLayoutManager(this)
    }

    private fun onItem(f: File) {
        if (f.isDirectory || rootDirPaths.contains(f.absolutePath)) {
            if (SafHelper.isRestricted(f) && !Elevated.available()) { SafHelper.openForRestricted(this, f); return }
            currentDir = f
            query = ""
            load()
        } else {
            if ((SafHelper.isRestricted(f) || !f.canRead()) && Elevated.available()) openViaElevated(f)
            else OpenHelper.open(this, f)
        }
    }

    /** Restricted files (e.g. under Android/data) can't be opened directly — copy them
     *  out with the elevated shell first, then hand the readable copy to the viewer. */
    private fun openViaElevated(f: File) {
        Toast.makeText(this, "Copying via ${Elevated.label()}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val copy = withContext(Dispatchers.IO) { Elevated.copyOut(f) }
            if (copy != null) OpenHelper.open(this@BrowseActivity, copy)
            else Toast.makeText(this@BrowseActivity, "Couldn't read this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goUp() {
        if (currentDir.absolutePath == startDir.absolutePath) {
            finish(); return
        }
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) {
            currentDir = parent
            query = ""
            load()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        when {
            adapter.selectionMode -> exitSelectionUi()
            currentDir.absolutePath != startDir.absolutePath -> goUp()
            else -> super.onBackPressed()
        }
    }

    private fun onSelectionCount(count: Int) {
        if (count == 0 && adapter.selectionMode) {
            exitSelectionUi()
        } else {
            supportActionBar?.title = "$count selected"
            supportActionBar?.subtitle = null
        }
    }

    private fun exitSelectionUi() {
        adapter.exitSelection()
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        invalidateOptionsMenu()
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = false
        rootDirPaths.clear()
        val restricted = SafHelper.isRestricted(currentDir)
        val raw = currentDir.listFiles()
        val unreadable = raw == null
        var files = raw?.toMutableList() ?: mutableListOf()
        var elevatedUsed = false
        if ((unreadable || restricted) && Elevated.available()) {
            files = elevatedList(currentDir); elevatedUsed = true
        }
        if (!showHidden) files.retainAll { !it.name.startsWith(".") }
        Sorting.sort(files, sortBy, asc)
        fullList = files
        applyFilter()
        binding.empty.text = if ((unreadable || restricted) && !elevatedUsed)
            "Can't read this folder — grant root or Shizuku (Dev tab) to open Android/data"
        else "This folder is empty"
        title = if (currentDir.absolutePath == StorageUtil.primaryStorage().absolutePath)
            "Internal storage" else currentDir.name
        supportActionBar?.subtitle = currentDir.absolutePath
        binding.pasteBar.visibility = if (Clipboard.has()) android.view.View.VISIBLE else android.view.View.GONE
        binding.pasteText.text = if (Clipboard.has()) {
            val verb = if (Clipboard.move) "Move" else "Copy"
            if (Clipboard.count() == 1) "$verb \"${Clipboard.files[0].name}\" here"
            else "$verb ${Clipboard.count()} items here"
        } else ""
    }

    private fun applyFilter() {
        val shown = if (query.isBlank()) fullList
        else fullList.filter { it.name.contains(query, ignoreCase = true) }
        adapter.submit(shown)
        binding.empty.visibility =
            if (shown.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (adapter.selectionMode) {
            menuInflater.inflate(R.menu.selection_menu, menu)
            return true
        }
        menuInflater.inflate(R.menu.browse_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search in folder"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText ?: ""
                applyFilter()
                return true
            }
        })
        if (openSearch) { searchItem.expandActionView(); openSearch = false }
        menu.findItem(R.id.action_view_toggle)?.setIcon(
            if (grid) R.drawable.ic_list else R.drawable.ic_grid)
        menu.findItem(R.id.action_hidden)?.isChecked = showHidden
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (adapter.selectionMode) {
            val sel = adapter.selectedList()
            when (item.itemId) {
                R.id.sel_copy -> {
                    Clipboard.set(sel, false)
                    Toast.makeText(this, "${sel.size} copied — open a folder and Paste", Toast.LENGTH_SHORT).show()
                    exitSelectionUi()
                }
                R.id.sel_move -> {
                    Clipboard.set(sel, true)
                    Toast.makeText(this, "${sel.size} cut — open a folder and Paste", Toast.LENGTH_SHORT).show()
                    exitSelectionUi()
                }
                R.id.sel_delete -> FileOps.deleteMultiple(this, sel) { exitSelectionUi() }
                R.id.sel_compress_zip -> ArchiveBridge.compressZip(this, sel) { exitSelectionUi() }
                R.id.sel_compress_7z -> ArchiveBridge.compress7z(this, sel) { exitSelectionUi() }
                R.id.sel_checksum -> FileOps.checksumMultiple(this, sel)
                R.id.sel_share -> OpenHelper.shareMultiple(this, sel)
                R.id.sel_all -> adapter.selectAllVisible()
            }
            return true
        }
        when (item.itemId) {
            R.id.action_view_toggle -> {
                grid = !grid
                adapter.grid = grid
                applyLayoutManager()
                adapter.submit(if (query.isBlank()) fullList else
                    fullList.filter { it.name.contains(query, true) })
                invalidateOptionsMenu()
            }
            R.id.sort_name -> { sortBy = Sorting.By.NAME; load() }
            R.id.sort_date -> { sortBy = Sorting.By.DATE; load() }
            R.id.sort_size -> { sortBy = Sorting.By.SIZE; load() }
            R.id.sort_type -> { sortBy = Sorting.By.TYPE; load() }
            R.id.sort_asc -> { asc = !asc; item.isChecked = asc; load() }
            R.id.action_hidden -> { showHidden = !showHidden; item.isChecked = showHidden; load() }
            R.id.action_new_folder -> newFolderDialog()
            R.id.action_paste -> if (Clipboard.has())
                FileOps.paste(this, currentDir, lifecycleScope) { load() }
                else Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun elevatedList(dir: File): MutableList<File> {
        val out = mutableListOf<File>()
        elevSizes.clear()
        // `ls -lA` carries sizes + a type flag; File.length()/isDirectory are 0/false for
        // restricted entries the app can't stat, so we parse them from the shell instead.
        val r = Elevated.sh("ls -lA \"${dir.absolutePath}\"")
        if (r.ok) {
            r.stdout.lineSequence().forEach { raw ->
                val line = raw.trimEnd()
                if (line.isBlank() || line.startsWith("total ")) return@forEach
                val m = lsLine.find(line) ?: return@forEach
                val perms = m.groupValues[1]
                val size = m.groupValues[2].toLongOrNull() ?: 0L
                var name = m.groupValues[3]
                if (" -> " in name) name = name.substringBefore(" -> ")   // strip symlink target
                if (name == "." || name == "..") return@forEach
                val f = File(dir, name)
                if (perms.startsWith("d")) rootDirPaths.add(f.absolutePath)
                else elevSizes[f.absolutePath] = size
                out.add(f)
            }
        }
        return out
    }

    private fun newFolderDialog() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT; hint = "Folder name" }
        AlertDialog.Builder(this)
            .setTitle("New folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    if (File(currentDir, n).mkdirs()) load()
                    else Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
