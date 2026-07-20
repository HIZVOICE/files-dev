package com.hyperfiles.manager

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.hyperfiles.manager.databinding.ActivityImageViewerBinding
import java.io.File
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private var images: List<File> = emptyList()
    private var index = 0
    private var draggingDown = false
    private var chromeShown = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        val (list, idx) = Playlist.imageSiblings(file)
        images = list
        index = idx
        show(index, 0)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { images.getOrNull(index)?.let { OpenHelper.share(this, it) } }
        binding.btnInfo.setOnClickListener { infoCurrent() }
        binding.btnDelete.setOnClickListener { deleteCurrent() }

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { toggleChrome(); return true }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null) return false
                val ty = e2.y - e1.y
                val tx = e2.x - e1.x
                if (!draggingDown && ty > 0 && abs(ty) > abs(tx) * 1.5f) draggingDown = true
                if (draggingDown) {
                    val d = ty.coerceAtLeast(0f)
                    binding.image.translationY = d
                    val frac = (d / binding.root.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    binding.root.alpha = 1f - frac * 0.7f
                    return true
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                if (abs(vy) > abs(vx) && vy > 1400) { dismissDown(); return true }
                if (abs(vx) > 900 && abs(vx) > abs(vy)) { if (vx < 0) next() else prev(); return true }
                return false
            }
        })
        binding.image.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (draggingDown) {
                    draggingDown = false
                    if (binding.image.translationY > binding.root.height * 0.20f) dismissDown()
                    else {
                        binding.image.animate().translationY(0f).setDuration(150).start()
                        binding.root.animate().alpha(1f).setDuration(150).start()
                    }
                }
            }
            true
        }
    }

    private fun next() { if (images.size > 1) show((index + 1) % images.size, +1) }
    private fun prev() { if (images.size > 1) show((index - 1 + images.size) % images.size, -1) }

    private fun show(i: Int, dir: Int) {
        index = i
        val f = images[i]
        binding.imgTitle.text = f.name
        binding.imgSubtitle.text = buildString {
            if (images.size > 1) append("${i + 1} / ${images.size}  ·  ")
            append(StorageUtil.formatSize(f.length()))
        }
        if (dir != 0) {
            val w = binding.image.width.takeIf { it > 0 } ?: 1000
            binding.image.translationX = (dir * w).toFloat()
            binding.image.animate().translationX(0f).setDuration(180).start()
        }
        Glide.with(this).load(f).fitCenter().into(binding.image)
    }

    private fun toggleChrome() {
        chromeShown = !chromeShown
        binding.chrome.animate().alpha(if (chromeShown) 1f else 0f).setDuration(180)
            .withStartAction { if (chromeShown) binding.chrome.visibility = View.VISIBLE }
            .withEndAction { if (!chromeShown) binding.chrome.visibility = View.INVISIBLE }
            .start()
    }

    private fun infoCurrent() {
        val f = images.getOrNull(index) ?: return
        val msg = "Name: ${f.name}\n\nPath: ${f.absolutePath}\n\nSize: ${StorageUtil.formatSize(f.length())}\n\n" +
            "Modified: ${StorageUtil.formatDate(f.lastModified())}"
        AlertDialog.Builder(this).setTitle("Details").setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun deleteCurrent() {
        val f = images.getOrNull(index) ?: return
        val bin = Prefs.useRecycleBin(this)
        AlertDialog.Builder(this)
            .setTitle(if (bin) "Move to Recycle bin" else "Delete")
            .setMessage(if (bin) "Move \"${f.name}\" to the Recycle bin? You can restore it later."
            else "Permanently delete \"${f.name}\"? This cannot be undone.")
            .setPositiveButton(if (bin) "Move" else "Delete") { _, _ ->
                Thread {
                    val ok = if (bin) TrashBin.trash(this, f) else f.delete()
                    runOnUiThread {
                        if (ok) { FileScanner.invalidate(); removeAndAdvance() }
                        else android.widget.Toast.makeText(this, "Delete failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeAndAdvance() {
        images = images.toMutableList().also { if (index < it.size) it.removeAt(index) }
        if (images.isEmpty()) { finish(); return }
        if (index >= images.size) index = images.size - 1
        show(index, 0)
    }

    private fun dismissDown() {
        val v = binding.root
        v.animate().translationY(v.height.toFloat()).alpha(0f).setDuration(200)
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }
}
