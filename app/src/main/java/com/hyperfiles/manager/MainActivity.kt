package com.hyperfiles.manager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hyperfiles.manager.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentsAdapter: FileAdapter
    private lateinit var homeFilesAdapter: FileAdapter
    private lateinit var tileAdapter: TileAdapter
    private var currentTab = R.id.nav_home
    private var showHidden = false
    private var sortBy = Sorting.By.NAME
    private var sortAsc = true
    private var homeGrid = false
    private var selAdapter: FileAdapter? = null
    private var categoriesExpanded = false
    private val primaryCount = 4

    private val tiles = listOf(
        Tile("documents", "Docs", R.drawable.ic_doc, color = 0xFFF5A623.toInt()),
        Tile("images", "Images", R.drawable.ic_image, color = 0xFFFF5C5C.toInt()),
        Tile("videos", "Videos", R.drawable.ic_video, color = 0xFF6C7BFF.toInt()),
        Tile("audio", "Music", R.drawable.ic_audio, color = 0xFF1FBF75.toInt()),
        Tile("archives", "Archives", R.drawable.ic_archive, color = 0xFFEAB308.toInt()),
        Tile("apks", "Apps", R.drawable.ic_apk, color = 0xFF38BDF8.toInt()),
        Tile("downloads", "Downloads", R.drawable.ic_download, color = 0xFF34D399.toInt())
    )

    private val devTiles = listOf(
        Tile("terminal", "Terminal", R.drawable.ic_terminal),
        Tile("sysinfo", "System info", R.drawable.ic_info),
        Tile("root", "Root /", R.drawable.ic_chip),
        Tile("system", "/system", R.drawable.ic_folder),
        Tile("vendor", "/vendor", R.drawable.ic_folder),
        Tile("data", "/data", R.drawable.ic_folder),
        Tile("shizuku", "Android/data", R.drawable.ic_storage),
        Tile("mount", "Mount RW", R.drawable.ic_chip),
        Tile("newdoc", "New doc", R.drawable.ic_doc)
    )

    private val legacyPermLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.categoryGrid.layoutManager = GridLayoutManager(this, 2)
        binding.categoryGrid.isNestedScrollingEnabled = false
        renderCategoryTiles()

        binding.devGrid.layoutManager = GridLayoutManager(this, 4)
        binding.devGrid.adapter = TileAdapter(devTiles, R.layout.item_tile_vertical) { onDevTile(it) }
        binding.devGrid.isNestedScrollingEnabled = false

        homeFilesAdapter = FileAdapter(
            onClick = { onFile(it) },
            onMore = { f, v -> FileOps.showMenu(this, f, v) { loadHomeFiles() } },
            selectable = true
        )
        homeFilesAdapter.onEnterSelectionMode = { enterSelection(homeFilesAdapter) }
        homeFilesAdapter.onSelectionChanged = { c -> onSelectionCount(c) }
        applyHomeLayout()

        recentsAdapter = FileAdapter(
            onClick = { OpenHelper.open(this, it) },
            onMore = { f, v -> FileOps.showMenu(this, f, v) { loadCategoriesAndRecents() } },
            selectable = true
        )
        recentsAdapter.onEnterSelectionMode = { enterSelection(recentsAdapter) }
        recentsAdapter.onSelectionChanged = { c -> onSelectionCount(c) }
        binding.recentsList.layoutManager = LinearLayoutManager(this)
        binding.recentsList.adapter = recentsAdapter

        Bounce.attach(binding.homeFiles)
        Bounce.attach(binding.recentsList)
        hideNavOnScroll(binding.homeFiles)
        hideNavOnScroll(binding.recentsList)
        binding.homeSwipe.setOnRefreshListener {
            FileScanner.invalidate()
            loadHomeFiles()
            loadCategoriesAndRecents()
            binding.homeSwipe.isRefreshing = false
        }

        binding.searchButton.setOnClickListener { v ->
            tap(v) {
                startActivity(Intent(this, BrowseActivity::class.java)
                    .putExtra(OpenHelper.EXTRA_PATH, StorageUtil.primaryStorage().absolutePath)
                    .putExtra(BrowseActivity.EXTRA_SEARCH, true))
            }
        }
        binding.browseRootBtn.setOnClickListener { v -> tap(v) { browse("/") } }
        binding.grantButton.setOnClickListener { v -> Anim.bounce(v); requestAccess() }
        binding.filterButton.setOnClickListener { v -> Anim.bounce(v); showViewSettings() }
        binding.overflowButton.setOnClickListener { v -> Anim.bounce(v); showOverflowMenu(v) }
        binding.newFolderBtn.setOnClickListener { v -> Anim.bounce(v); FileOps.newFolder(this, StorageUtil.primaryStorage()) { loadHomeFiles() } }
        binding.moreCategoriesBtn.setOnClickListener { v -> Anim.bounce(v); toggleCategories() }
        binding.cleanCacheBtn.setOnClickListener { v -> Anim.bounce(v); cleanCache() }
        binding.findDupBtn.setOnClickListener { v -> tap(v) { startActivity(Intent(this, DuplicatesActivity::class.java)) } }
        binding.recycleBinBtn.setOnClickListener { v -> tap(v) { startActivity(Intent(this, RecycleBinActivity::class.java)) } }
        binding.secureButton.setOnClickListener { v -> tap(v) { startActivity(Intent(this, LockActivity::class.java)) } }

        binding.selClose.setOnClickListener { exitSelection() }
        binding.selShareBtn.setOnClickListener { selList()?.let { OpenHelper.shareMultiple(this, it) } }
        binding.selCopyBtn.setOnClickListener {
            selList()?.let {
                Clipboard.set(it, false)
                Toast.makeText(this, "${it.size} copied — open a folder and Paste", Toast.LENGTH_SHORT).show()
                exitSelection()
            }
        }
        binding.selDeleteBtn.setOnClickListener {
            selList()?.let { FileOps.deleteMultiple(this, it) { exitSelection(); refresh() } }
        }
        binding.selAllBtn.setOnClickListener { selAdapter?.selectAllVisible() }
        binding.analyzerBtn.setOnClickListener { v -> tap(v) { startActivity(Intent(this, StorageAnalyzerActivity::class.java)) } }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (selAdapter != null) exitSelection()
            currentTab = item.itemId
            applyTabVisibility()
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val access = StorageUtil.hasAllFilesAccess(this)
        binding.permissionCard.visibility = if (access) View.GONE else View.VISIBLE
        binding.headerBar.visibility = if (access) View.VISIBLE else View.GONE
        binding.bottomNav.visibility = if (access) View.VISIBLE else View.GONE
        if (!access) {
            binding.homeSwipe.visibility = View.GONE
            binding.recentContainer.visibility = View.GONE
            binding.devsContainer.visibility = View.GONE
            // Auto-prompt for storage access on first launch after install
            if (!Prefs.autoPermDone(this)) {
                Prefs.setAutoPermDone(this, true)
                requestAccess()
            }
            return
        }
        val showDev = Prefs.showDevTools(this)
        binding.bottomNav.menu.findItem(R.id.nav_devs).isVisible = showDev
        if (!showDev && currentTab == R.id.nav_devs) {
            currentTab = R.id.nav_home
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
        applyTabVisibility()
        buildQuickAccess()
        loadHomeFiles()
        loadCategoriesAndRecents()
    }

    private fun applyTabVisibility() {
        binding.homeSwipe.visibility = if (currentTab == R.id.nav_home) View.VISIBLE else View.GONE
        binding.recentContainer.visibility = if (currentTab == R.id.nav_recent) View.VISIBLE else View.GONE
        binding.devsContainer.visibility = if (currentTab == R.id.nav_devs) View.VISIBLE else View.GONE
        binding.bigTitle.text = when (currentTab) {
            R.id.nav_recent -> "Recent"
            R.id.nav_devs -> "Devs"
            else -> "Storage"
        }
        // Sort / secure-folder only apply to the Storage tab.
        val onStorage = currentTab == R.id.nav_home
        binding.filterButton.visibility = if (onStorage) View.VISIBLE else View.GONE
        binding.secureButton.visibility = if (onStorage) View.VISIBLE else View.GONE
        setNavHidden(false)
    }

    // ---- Hide bottom nav while scrolling ----
    private fun hideNavOnScroll(rv: androidx.recyclerview.widget.RecyclerView) {
        rv.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(r: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (selAdapter != null) return
                if (dy > 8) setNavHidden(true) else if (dy < -8) setNavHidden(false)
            }
        })
    }

    private fun setNavHidden(hidden: Boolean) {
        val nav = binding.bottomNav
        if (nav.visibility != View.VISIBLE) return
        val target = if (hidden) nav.height.toFloat() else 0f
        if (nav.translationY == target) return
        nav.animate().translationY(target).setDuration(200).start()
    }

    // ---- Multi-select on Home / Recent lists ----
    private fun selList(): List<File>? = selAdapter?.selectedList()?.takeIf { it.isNotEmpty() }

    private fun enterSelection(adapter: FileAdapter) {
        selAdapter = adapter
        binding.headerIcons.visibility = View.GONE
        binding.bigTitle.visibility = View.GONE
        binding.selectionBar.visibility = View.VISIBLE
    }

    private fun onSelectionCount(count: Int) {
        if (count == 0) { exitSelection(); return }
        binding.selCount.text = "$count selected"
    }

    private fun exitSelection() {
        selAdapter?.exitSelection()
        selAdapter = null
        binding.selectionBar.visibility = View.GONE
        binding.headerIcons.visibility = View.VISIBLE
        binding.bigTitle.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        if (selAdapter != null) exitSelection() else super.onBackPressed()
    }

    private fun buildQuickAccess() {
        val primary = StorageUtil.primaryStorage()
        val shortcuts = listOf(
            "Downloads" to StorageUtil.downloadsDir(),
            "Camera" to File(primary, "DCIM"),
            "Pictures" to File(primary, "Pictures"),
            "Screenshots" to File(primary, "Pictures/Screenshots"),
            "Documents" to File(primary, "Documents"),
            "Movies" to File(primary, "Movies"),
            "Music" to File(primary, "Music")
        )
        val row = binding.quickAccessRow
        row.removeAllViews()
        var any = false
        for ((label, dir) in shortcuts) {
            if (!dir.exists() || !dir.isDirectory) continue
            any = true
            val chip = Chip(this).apply {
                text = label
                isCheckable = false
                isClickable = true
                setOnClickListener { v -> tap(v) { browse(dir.absolutePath) } }
            }
            row.addView(chip)
        }
        val vis = if (any) View.VISIBLE else View.GONE
        binding.quickAccessLabel.visibility = vis
        binding.quickAccessScroll.visibility = vis
    }

    private fun browse(path: String) {
        startActivity(Intent(this, BrowseActivity::class.java).putExtra(OpenHelper.EXTRA_PATH, path))
    }

    private fun tap(v: View, action: () -> Unit) {
        Anim.bounce(v)
        v.postDelayed(action, 90)
    }

    private fun onFile(f: File) {
        if (f.isDirectory) {
            if (SafHelper.isRestricted(f)) SafHelper.openForRestricted(this, f) else browse(f.absolutePath)
        } else {
            OpenHelper.open(this, f)
        }
    }

    private fun cleanCache() {
        lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) { Cleaner.cleanCache(this@MainActivity) }
            Toast.makeText(this@MainActivity,
                "Freed ${StorageUtil.formatSize(freed)} of cache", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermLauncher.launch(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun loadHomeFiles() {
        val files = StorageUtil.primaryStorage().listFiles()?.toMutableList() ?: mutableListOf()
        if (!showHidden) files.retainAll { !it.name.startsWith(".") }
        Sorting.sort(files, sortBy, asc = sortAsc)
        homeFilesAdapter.submit(files)
    }

    private fun loadCategoriesAndRecents() {
        binding.recentsProgress.visibility = View.VISIBLE
        binding.recentsEmpty.visibility = View.GONE
        lifecycleScope.launch {
            val map = FileScanner.scan(StorageUtil.primaryStorage(), force = false)
            val recents = FileScanner.recents(map)
            recentsAdapter.submit(recents)
            binding.recentsProgress.visibility = View.GONE
            binding.recentsEmpty.visibility = if (recents.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun renderCategoryTiles() {
        val visible = if (categoriesExpanded) tiles else tiles.take(primaryCount)
        tileAdapter = TileAdapter(visible, R.layout.item_category) { onTile(it) }
        binding.categoryGrid.adapter = tileAdapter
        binding.moreCategoriesLabel.text = if (categoriesExpanded) "Less" else "More"
        binding.moreCategoriesChevron.rotation = if (categoriesExpanded) 270f else 90f
    }

    private fun toggleCategories() {
        categoriesExpanded = !categoriesExpanded
        renderCategoryTiles()
    }

    private fun applyHomeLayout() {
        homeFilesAdapter.grid = homeGrid
        homeFilesAdapter.media = false
        binding.homeFiles.layoutManager =
            if (homeGrid) GridLayoutManager(this, 3) else LinearLayoutManager(this)
        binding.homeFiles.adapter = homeFilesAdapter
    }

    /** HyperOS-style "Feature settings" sheet: layout, sort by, sort order, display. */
    private fun showViewSettings() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_view_settings, null)
        sheet.setContentView(v)

        fun mark(id: Int, on: Boolean) {
            v.findViewById<View>(id).visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        fun refresh() {
            mark(R.id.checkLayoutList, !homeGrid); mark(R.id.checkLayoutGrid, homeGrid)
            mark(R.id.checkSortName, sortBy == Sorting.By.NAME)
            mark(R.id.checkSortSize, sortBy == Sorting.By.SIZE)
            mark(R.id.checkSortTime, sortBy == Sorting.By.DATE)
            mark(R.id.checkSortType, sortBy == Sorting.By.TYPE)
            mark(R.id.checkOrderForward, sortAsc); mark(R.id.checkOrderReverse, !sortAsc)
            mark(R.id.checkDisplayAll, !showHidden); mark(R.id.checkDisplayHidden, showHidden)
        }
        refresh()

        v.findViewById<View>(R.id.rowLayoutList).setOnClickListener { homeGrid = false; applyHomeLayout(); refresh() }
        v.findViewById<View>(R.id.rowLayoutGrid).setOnClickListener { homeGrid = true; applyHomeLayout(); refresh() }
        v.findViewById<View>(R.id.rowSortName).setOnClickListener { sortBy = Sorting.By.NAME; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowSortSize).setOnClickListener { sortBy = Sorting.By.SIZE; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowSortTime).setOnClickListener { sortBy = Sorting.By.DATE; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowSortType).setOnClickListener { sortBy = Sorting.By.TYPE; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowOrderForward).setOnClickListener { sortAsc = true; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowOrderReverse).setOnClickListener { sortAsc = false; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowDisplayAll).setOnClickListener { showHidden = false; loadHomeFiles(); refresh() }
        v.findViewById<View>(R.id.rowDisplayHidden).setOnClickListener { showHidden = true; loadHomeFiles(); refresh() }
        sheet.show()
    }

    private fun showOverflowMenu(v: View) {
        val popup = android.widget.PopupMenu(this, v)
        popup.menu.add(if (showHidden) "Hide hidden files" else "Show hidden files")
        popup.menu.add("Clean cache")
        popup.menu.add("Find duplicates")
        popup.menu.add("Settings")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Show hidden files", "Hide hidden files" -> { showHidden = !showHidden; loadHomeFiles() }
                "Clean cache" -> cleanCache()
                "Find duplicates" -> startActivity(Intent(this, DuplicatesActivity::class.java))
                "Settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun onDevTile(t: Tile) {
        when (t.id) {
            "terminal" -> startActivity(Intent(this, TerminalActivity::class.java))
            "sysinfo" -> startActivity(Intent(this, SysInfoActivity::class.java))
            "shizuku" -> setupShizuku()
            "mount" -> RootOps.mountDialog(this)
            "newdoc" -> NewDocument.dialog(this) { browse(it) }
            else -> {
                val path = when (t.id) {
                    "root" -> "/"; "system" -> "/system"; "vendor" -> "/vendor"; "data" -> "/data"; else -> "/"
                }
                browse(path)
            }
        }
    }

    /** Grant ADB-level access via Shizuku so Android/data can be browsed in-app. */
    private fun setupShizuku() {
        val target = File(StorageUtil.primaryStorage(), "Android/data").absolutePath
        when {
            ShizukuAccess.granted() -> browse(target)
            !ShizukuAccess.installedOrRunning() -> shizukuInfoDialog()
            else -> ShizukuAccess.request { ok ->
                runOnUiThread {
                    if (ok) browse(target)
                    else Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shizukuInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Shizuku for Android/data")
            .setMessage(
                "Android/data and Android/obb are locked by the system on Android 11+. " +
                    "Shizuku grants ADB-level access so Files Dev can browse and open them.\n\n" +
                    "1. Install the Shizuku app.\n" +
                    "2. Start the Shizuku service (Wireless debugging on-device, or ADB).\n" +
                    "3. Return here and tap Android/data again to authorize."
            )
            .setPositiveButton("Get Shizuku") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun onTile(t: Tile) {
        when (t.id) {
            "downloads" -> browse(StorageUtil.downloadsDir().absolutePath)
            "all" -> browse(StorageUtil.primaryStorage().absolutePath)
            else -> {
                val cat = when (t.id) {
                    "images" -> FileCategory.IMAGES
                    "videos" -> FileCategory.VIDEOS
                    "audio" -> FileCategory.AUDIO
                    "documents" -> FileCategory.DOCUMENTS
                    "archives" -> FileCategory.ARCHIVES
                    "apks" -> FileCategory.APKS
                    else -> FileCategory.OTHER
                }
                startActivity(Intent(this, CategoryActivity::class.java)
                    .putExtra(CategoryActivity.EXTRA_CATEGORY, cat.name))
            }
        }
    }
}
