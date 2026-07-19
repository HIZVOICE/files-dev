package com.hyperfiles.manager

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.hyperfiles.manager.databinding.ActivityImageViewerBinding
import java.io.File
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private var images: List<File> = emptyList()
    private var index = 0

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
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || abs(vx) < 900 || abs(vx) <= abs(vy)) return false
                if (vx < 0) next() else prev()   // swipe left = next, swipe right = previous
                return true
            }
        })
        binding.image.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
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
}
