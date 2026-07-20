package com.hyperfiles.manager

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hyperfiles.manager.databinding.ActivityCategoryBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class CategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
    }

    private lateinit var binding: ActivityCategoryBinding
    private lateinit var adapter: FileAdapter
    private lateinit var category: FileCategory

    private var allFiles: List<File> = emptyList()
    private var shown: List<File> = emptyList()
    private var currentFolder: String? = null
    private var query: String = ""
    private var useGrid = false
    private var mediaCategory = false
    private var toggleItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        category = try {
            FileCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY) ?: FileCategory.OTHER.name)
        } catch (e: Exception) { FileCategory.OTHER }
        // All categories use the rich media row (rounded thumbnail + size • date);
        // thumbnails resolve to photos/video frames, album art, or file-type glyphs.
        mediaCategory = true

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            if (adapter.selectionMode) exitSelectionUi() else finish()
        }
        title = category.title

        adapter = FileAdapter(
            onClick = { OpenHelper.open(this, it) },
            onMore = { f, v -> FileOps.showMenu(this, f, v) { reload() } },
            selectable = true   // long-press (or a tap in select mode) selects, doesn't open the menu
        )
        adapter.onEnterSelectionMode = {
            binding.toolbar.setNavigationIcon(R.drawable.ic_close)
            invalidateOptionsMenu()
        }
        adapter.onSelectionChanged = { count ->
            if (count == 0 && adapter.selectionMode) exitSelectionUi()
            else { supportActionBar?.title = "$count selected"; supportActionBar?.subtitle = null }
        }
        applyLayout()
        Bounce.attach(binding.list)

        binding.swipe.setOnRefreshListener { FileScanner.invalidate(); reload() }
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (adapter.selectionMode) {
            menuInflater.inflate(R.menu.selection_menu, menu)
            return true
        }
        menuInflater.inflate(R.menu.category_menu, menu)
        toggleItem = menu.findItem(R.id.action_view_toggle)
        toggleItem?.setIcon(if (useGrid) R.drawable.ic_list else R.drawable.ic_grid)
        val search = menu.findItem(R.id.action_search)?.actionView as? SearchView
        search?.queryHint = "Search ${category.title.lowercase()}"
        search?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText ?: ""; applyFilter(); return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (adapter.selectionMode) {
            val sel = adapter.selectedList()
            when (item.itemId) {
                R.id.sel_share -> OpenHelper.shareMultiple(this, sel)
                R.id.sel_copy -> { Clipboard.set(sel, false); toast("${sel.size} copied — open a folder and Paste"); exitSelectionUi() }
                R.id.sel_move -> { Clipboard.set(sel, true); toast("${sel.size} cut — open a folder and Paste"); exitSelectionUi() }
                R.id.sel_delete -> FileOps.deleteMultiple(this, sel) { exitSelectionUi(); reload() }
                R.id.sel_compress_zip -> ArchiveBridge.compressZip(this, sel) { exitSelectionUi(); reload() }
                R.id.sel_compress_7z -> ArchiveBridge.compress7z(this, sel) { exitSelectionUi(); reload() }
                R.id.sel_checksum -> FileOps.checksumMultiple(this, sel)
                R.id.sel_all -> adapter.selectAllVisible()
            }
            return true
        }
        return when (item.itemId) {
            R.id.action_view_toggle -> { useGrid = !useGrid; applyLayout(); applyFilter(); true }
            R.id.action_select -> { adapter.enterSelectionMode(); true }
            R.id.action_select_date -> { selectByDateDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (adapter.selectionMode) exitSelectionUi() else super.onBackPressed()
    }

    private fun exitSelectionUi() {
        adapter.exitSelection()
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        supportActionBar?.title = category.title
        supportActionBar?.subtitle = "${shown.size} items"
        invalidateOptionsMenu()
    }

    private fun selectByDateDialog() {
        val labels = arrayOf("Today", "Yesterday", "Last 7 days", "Last 30 days")
        AlertDialog.Builder(this)
            .setTitle("Select by date")
            .setItems(labels) { _, which -> selectByRange(which) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectByRange(which: Int) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val (from, to) = when (which) {
            0 -> startOfToday to now                    // Today
            1 -> (startOfToday - day) to startOfToday    // Yesterday
            2 -> (now - 7 * day) to now                  // Last 7 days
            else -> (now - 30 * day) to now              // Last 30 days
        }
        val matches = shown.filter { it.lastModified() in from until to || it.lastModified() == to }
        if (matches.isEmpty()) { toast("Nothing from that range"); return }
        adapter.selectOnly(matches)
        toast("Selected ${matches.size}")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun applyLayout() {
        if (useGrid) {
            adapter.grid = true; adapter.media = false
            binding.list.layoutManager = GridLayoutManager(this, 3)
        } else {
            adapter.grid = false; adapter.media = mediaCategory
            binding.list.layoutManager = LinearLayoutManager(this)
        }
        binding.list.adapter = adapter
        toggleItem?.setIcon(if (useGrid) R.drawable.ic_list else R.drawable.ic_grid)
    }

    private fun reload() {
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        lifecycleScope.launch {
            val map = FileScanner.scan(StorageUtil.primaryStorage(), force = false)
            val list = (map[category] ?: emptyList()).toMutableList()
            Sorting.sort(list, Sorting.By.DATE, asc = false)
            allFiles = list
            buildChips(list)
            applyFilter()
            binding.progress.visibility = View.GONE
            binding.swipe.isRefreshing = false
        }
    }

    private fun buildChips(files: List<File>) {
        val group = binding.folderChips
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        val folders = files.mapNotNull { it.parentFile?.name }.distinct().sortedBy { it.lowercase() }

        fun addChip(label: String, tag: String?) {
            val chip = Chip(this)
            chip.text = label
            chip.isCheckable = true
            chip.tag = tag ?: ""
            group.addView(chip)
            if (tag == currentFolder) chip.isChecked = true
        }
        addChip("All", null)
        folders.forEach { addChip(it, it) }
        if (group.checkedChipId == View.NO_ID) (group.getChildAt(0) as? Chip)?.isChecked = true

        group.setOnCheckedStateChangeListener { g, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = g.findViewById<Chip>(id)
            currentFolder = (chip?.tag as? String)?.ifEmpty { null }
            applyFilter()
        }
        binding.chipScroll.visibility = if (folders.size > 1) View.VISIBLE else View.GONE
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val filtered = allFiles.filter { f ->
            (currentFolder == null || f.parentFile?.name == currentFolder) &&
                (q.isEmpty() || f.name.lowercase().contains(q))
        }
        shown = filtered
        adapter.submit(filtered)
        binding.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (!adapter.selectionMode) supportActionBar?.subtitle = "${filtered.size} items"
    }
}
