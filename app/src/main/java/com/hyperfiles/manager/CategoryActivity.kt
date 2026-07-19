package com.hyperfiles.manager

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hyperfiles.manager.databinding.ActivityCategoryBinding
import kotlinx.coroutines.launch
import java.io.File

class CategoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
    }

    private lateinit var binding: ActivityCategoryBinding
    private lateinit var adapter: FileAdapter
    private lateinit var category: FileCategory

    private var allFiles: List<File> = emptyList()
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
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = category.title

        adapter = FileAdapter(
            onClick = { OpenHelper.open(this, it) },
            onMore = { f, v -> FileOps.showMenu(this, f, v) { reload() } }
        )
        applyLayout()

        binding.swipe.setOnRefreshListener { FileScanner.invalidate(); reload() }
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
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
        return when (item.itemId) {
            R.id.action_view_toggle -> {
                useGrid = !useGrid
                applyLayout()
                applyFilter()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
        adapter.submit(filtered)
        binding.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = "${filtered.size} items"
    }
}
