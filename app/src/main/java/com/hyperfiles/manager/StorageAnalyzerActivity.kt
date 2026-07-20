package com.hyperfiles.manager

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyperfiles.manager.databinding.ActivityStorageAnalyzerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shows device storage usage and a size breakdown by file type. */
class StorageAnalyzerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStorageAnalyzerBinding

    private data class Cat(val category: FileCategory, val label: String, val color: Int)

    private val cats = listOf(
        Cat(FileCategory.IMAGES, "Images", 0xFFFF5C5C.toInt()),
        Cat(FileCategory.VIDEOS, "Videos", 0xFF6C7BFF.toInt()),
        Cat(FileCategory.AUDIO, "Audio", 0xFF1FBF75.toInt()),
        Cat(FileCategory.DOCUMENTS, "Documents", 0xFFF5A623.toInt()),
        Cat(FileCategory.ARCHIVES, "Archives", 0xFFEAB308.toInt()),
        Cat(FileCategory.APKS, "Apps", 0xFF38BDF8.toInt()),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityStorageAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        analyze()
    }

    private fun analyze() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val root = StorageUtil.primaryStorage()
            val usage = withContext(Dispatchers.IO) { StorageUtil.usageOf(root) }
            val sizes = withContext(Dispatchers.IO) {
                val map = FileScanner.scan(root, force = false)
                cats.associate { it.category to (map[it.category]?.sumOf { f -> f.length() } ?: 0L) }
            }
            binding.progress.visibility = View.GONE
            render(usage, sizes)
        }
    }

    private fun render(usage: StorageUtil.Usage, sizes: Map<FileCategory, Long>) {
        binding.usageText.text = "${StorageUtil.formatSize(usage.used)} used"
        binding.usageSub.text = "of ${StorageUtil.formatSize(usage.total)} · ${StorageUtil.formatSize(usage.free)} free"
        binding.usageBar.progress = usage.percentUsed

        val mediaSum = sizes.values.sum()
        val used = usage.used.coerceAtLeast(1L)
        val other = (usage.used - mediaSum).coerceAtLeast(0L)

        binding.catContainer.removeAllViews()
        val rows = cats.map { Triple(it.label, sizes[it.category] ?: 0L, it.color) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second } +
            listOf(Triple("Other & system", other, 0xFF8A8F99.toInt()))

        for ((label, bytes, color) in rows) {
            if (bytes <= 0) continue
            addRow(label, bytes, (bytes.toDouble() / used).toFloat(), color)
        }
    }

    private fun addRow(label: String, bytes: Long, frac: Float, color: Int) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val name = TextView(this).apply {
            text = label
            setTextColor(0xFFECEDEF.toInt())
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val size = TextView(this).apply {
            text = "${StorageUtil.formatSize(bytes)}  ·  ${(frac * 100).toInt()}%"
            setTextColor(0xFF9AA0AA.toInt())
            textSize = 13f
        }
        top.addView(name); top.addView(size)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = (frac * 100).toInt().coerceIn(1, 100)
            progressTintList = ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (6 * dp).toInt()
            ).apply { topMargin = (6 * dp).toInt() }
        }
        row.addView(top); row.addView(bar)
        binding.catContainer.addView(row)
    }
}
