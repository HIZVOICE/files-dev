package com.hyperfiles.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.hyperfiles.manager.databinding.ActivityAudioBinding
import java.io.File

class AudioPlayerActivity : AppCompatActivity(), PlaybackService.Listener {

    private lateinit var binding: ActivityAudioBinding
    private var service: PlaybackService? = null
    private var bound = false

    private var playlist: List<File> = emptyList()
    private var startIndex = 0
    private var duration = 0L
    private var userSeeking = false

    private val notifPerm =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
            service = (b as PlaybackService.LocalBinder).service
            bound = true
            service?.listener = this@AudioPlayerActivity
            val paths = playlist.map { it.absolutePath }
            val cur = service?.currentName()
            if (service?.isPlaying() != true || cur != playlist.getOrNull(startIndex)?.name) {
                service?.setPlaylistAndPlay(paths, startIndex)
            } else {
                onTrack(service?.currentIndex() ?: startIndex, cur ?: "")
                onState(true)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; bound = false }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Swipe across the album art to change track (left = next, right = previous).
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val ax = kotlin.math.abs(vx); val ay = kotlin.math.abs(vy)
                if (ay > ax && vy > 1400) { dismissDown(); return true }        // swipe down = close
                if (ax < 900 || ax <= ay) return false
                if (vx < 0) service?.next() else service?.prev()                // left = next, right = prev
                return true
            }
        })
        binding.artArea.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        val (list, idx) = Playlist.siblings(file, audio = true)
        playlist = list
        startIndex = idx

        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        binding.btnPlayPause.setOnClickListener { Anim.bounce(it); service?.toggle() }
        binding.btnPrev.setOnClickListener { Anim.bounce(it); service?.prev() }
        binding.btnNext.setOnClickListener { Anim.bounce(it); service?.next() }
        binding.btnPlaylist.setOnClickListener { showPlaylist() }

        binding.seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser && duration > 0) binding.timeCurrent.text = fmt((duration * value / 1000f).toLong())
        }
        binding.seekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) { userSeeking = true }
            override fun onStopTrackingTouch(slider: Slider) {
                userSeeking = false
                if (duration > 0) service?.seekTo((duration * slider.value / 1000f).toLong())
            }
        })

        val svc = Intent(this, PlaybackService::class.java)
        ContextCompat.startForegroundService(this, svc)
        bindService(svc, connection, Context.BIND_AUTO_CREATE)
    }

    private fun showPlaylist() {
        if (playlist.isEmpty()) return
        val sheet = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_playlist, null)
        sheet.setContentView(v)
        // Let our rounded content define the corners (hide the default square sheet background).
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)

        v.findViewById<TextView>(R.id.sheetTitle).text = "Playlist (${playlist.size})"
        val container = v.findViewById<LinearLayout>(R.id.playlistContainer)
        val current = service?.currentIndex() ?: startIndex
        val density = resources.displayMetrics.density
        val padH = (density * 22).toInt()
        val padV = (density * 14).toInt()
        val bgAttr = TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }

        playlist.forEachIndexed { i, f ->
            val tv = TextView(this).apply {
                text = f.name
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(padH, padV, padH, padV)
                setTextColor(ContextCompat.getColor(this@AudioPlayerActivity,
                    if (i == current) R.color.hyper_blue else R.color.text_primary))
                setTypeface(typeface, if (i == current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setBackgroundResource(bgAttr.resourceId)
                isClickable = true
                setOnClickListener {
                    service?.setPlaylistAndPlay(playlist.map { it.absolutePath }, i)
                    sheet.dismiss()
                }
            }
            container.addView(tv)
        }
        sheet.show()
    }

    // ---- PlaybackService.Listener ----
    override fun onState(playing: Boolean) {
        runOnUiThread {
            binding.btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    override fun onTime(pos: Long, dur: Long) {
        runOnUiThread {
            duration = dur
            binding.timeCurrent.text = fmt(pos)
            binding.timeTotal.text = fmt(dur)
            if (!userSeeking && dur > 0) {
                binding.seekBar.value = (pos * 1000f / dur).coerceIn(0f, 1000f)
            }
        }
    }

    override fun onTrack(index: Int, name: String) {
        runOnUiThread {
            binding.trackTitle.text = name
            binding.trackSubtitle.text = "${index + 1} of ${playlist.size}"
            binding.seekBar.value = 0f
            binding.art.visibility = View.GONE
            binding.artGlyph.visibility = View.VISIBLE
            binding.artBackdrop.setImageDrawable(null)
            val path = playlist.getOrNull(index)?.absolutePath
            if (path != null) {
                Thumbs.loadArt(path, video = false) { bmp ->
                    if (service?.currentIndex() == index && bmp != null) {
                        binding.art.setImageBitmap(bmp)
                        binding.art.visibility = View.VISIBLE
                        binding.artGlyph.visibility = View.GONE
                        // Vibrant frosted backdrop derived from the album art.
                        binding.artBackdrop.setImageBitmap(bmp)
                        if (Build.VERSION.SDK_INT >= 31) {
                            binding.artBackdrop.setRenderEffect(
                                RenderEffect.createBlurEffect(72f, 72f, Shader.TileMode.CLAMP))
                        }
                    }
                }
            }
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    private fun dismissDown() {
        val v = binding.root
        v.animate().translationY(v.height.toFloat()).alpha(0f).setDuration(200)
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            service?.listener = null
            unbindService(connection)
            bound = false
        }
        // Service keeps running for background playback until Stop is tapped.
    }
}
