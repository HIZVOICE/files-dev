package com.hyperfiles.manager

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.hyperfiles.manager.databinding.ActivityImageViewerBinding
import java.io.File
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private var images: List<File> = emptyList()
    private var index = 0
    private var draggingDown = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        val (list, idx) = Playlist.imageSiblings(file)
        images = list
        index = idx
        show(index, 0)

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val bar = supportActionBar ?: return true
                if (bar.isShowing) bar.hide() else bar.show()
                return true
            }
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
                if (abs(vy) > abs(vx) && vy > 1400) { dismissDown(); return true }   // fast swipe down = close
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val item = menu.add(0, 1, 0, "Share")
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        item.icon = ContextCompat.getDrawable(this, R.drawable.ic_share)?.mutate()?.apply { setTint(Color.WHITE) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            images.getOrNull(index)?.let { OpenHelper.share(this, it) }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun next() { if (images.size > 1) show((index + 1) % images.size, +1) }
    private fun prev() { if (images.size > 1) show((index - 1 + images.size) % images.size, -1) }

    private fun show(i: Int, dir: Int) {
        index = i
        val f = images[i]
        title = f.name
        supportActionBar?.subtitle = if (images.size > 1) "${i + 1} / ${images.size}" else null
        if (dir != 0) {
            val w = binding.image.width.takeIf { it > 0 } ?: 1000
            binding.image.translationX = (dir * w).toFloat()
            binding.image.animate().translationX(0f).setDuration(180).start()
        }
        Glide.with(this).load(f).fitCenter().into(binding.image)
    }

    private fun dismissDown() {
        val v = binding.root
        v.animate().translationY(v.height.toFloat()).alpha(0f).setDuration(200)
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }
}
