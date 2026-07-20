package com.hyperfiles.manager

import android.content.res.ColorStateList
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

/** Device storage usage + a size breakdown by type (System, Apps, Videos, Audio, Photos, Documents). */
class StorageAnalyzerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStorageAnalyzerBinding

    private data class Seg(val label: String, val bytes: Long, val color: Int)

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
                fun sum(c: FileCategory) = map[c]?.sumOf { it.length() } ?: 0L
                mapOf(
                    "images" to sum(FileCategory.IMAGES),
                    "videos" to sum(FileCategory.VIDEOS),
                    "audio" to sum(FileCategory.AUDIO),
                    "docs" to (sum(FileCategory.DOCUMENTS) + sum(FileCategory.ARCHIVES)),
                    "apks" to sum(FileCategory.APKS)
                )
            }
            val apps = withContext(Dispatchers.IO) { installedAppsSize() }
            binding.progress.visibility = View.GONE
            render(usage, sizes, apps)
        }
    }

    /** Sum of installed apps' APK sizes (base + splits). APKs are world-readable, so no root needed. */
    private fun installedAppsSize(): Long {
        var total = 0L
        try {
            for (ai in packageManager.getInstalledApplications(0)) {
                try { total += File(ai.sourceDir).length() } catch (_: Exception) {}
                ai.splitSourceDirs?.forEach { sp -> try { total += File(sp).length() } catch (_: Exception) {} }
            }
        } catch (_: Exception) {}
        return total
    }

    private fun render(usage: StorageUtil.Usage, sizes: Map<String, Long>, appsApk: Long) {
        binding.usageText.text = "${StorageUtil.formatSize(usage.used)} used"
        binding.usageSub.text =
            "of ${StorageUtil.formatSize(usage.total)}  ·  ${StorageUtil.formatSize(usage.free)} free"
        binding.usageBar.progress = usage.percentUsed

        val images = sizes["images"] ?: 0L
        val videos = sizes["videos"] ?: 0L
        val audio = sizes["audio"] ?: 0L
        val docs = sizes["docs"] ?: 0L
        val apps = appsApk + (sizes["apks"] ?: 0L)
        val accounted = images + videos + audio + docs + apps
        val system = (usage.used - accounted).coerceAtLeast(0L)

        val segs = listOf(
            Seg("System & other", system, 0xFF8A8F99.toInt()),
            Seg("Apps", apps, 0xFF38BDF8.toInt()),
            Seg("Videos", videos, 0xFF6C7BFF.toInt()),
            Seg("Audio", audio, 0xFF1FBF75.toInt()),
            Seg("Photos", images, 0xFFFF5C5C.toInt()),
            Seg("Documents", docs, 0xFFF5A623.toInt())
        ).filter { it.bytes > 0 }.sortedByDescending { it.bytes }

        val maxBytes = (segs.maxOfOrNull { it.bytes } ?: 1L).coerceAtLeast(1L)
        val used = usage.used.coerceAtLeast(1L)

        binding.catContainer.removeAllViews()
        for (s in segs) {
            addRow(s.label, s.bytes, (s.bytes.toDouble() / maxBytes).toFloat(),
                (s.bytes.toDouble() / used * 100).toInt(), s.color)
        }
    }

    private fun addRow(label: String, bytes: Long, barFrac: Float, pctOfUsed: Int, color: Int) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (11 * dp).toInt(), 0, (11 * dp).toInt())
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFECEDEF.toInt())
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = "${StorageUtil.formatSize(bytes)}  ·  $pctOfUsed%"
            setTextColor(0xFF9AA0AA.toInt())
            textSize = 13f
        })
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = (barFrac * 100).toInt().coerceIn(2, 100)
            progressTintList = ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (7 * dp).toInt()
            ).apply { topMargin = (7 * dp).toInt() }
        }
        row.addView(top); row.addView(bar)
        binding.catContainer.addView(row)
    }
}
