package com.hyperfiles.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hyperfiles.manager.databinding.ActivityPlayerBinding
import java.io.File
import kotlin.math.abs

class PlayerActivity : AppCompatActivity(), VideoService.Listener {

    private lateinit var binding: ActivityPlayerBinding
    private var service: VideoService? = null
    private var bound = false

    private var playlist: List<File> = emptyList()
    private var startIndex = 0
    private var duration = 0L
    private var controlsVisible = true
    private var userSeeking = false
    private var muted = false
    private val ui = Handler(Looper.getMainLooper())

    private val aspectModes = listOf<String?>(null, "16:9", "4:3", "1:1")
    private var aspectIndex = 0
    private val orientations = listOf(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    )
    private var orientationIndex = 0
    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIndex = 2

    private enum class G { NONE, SEEK, BRIGHTNESS, VOLUME }
    private var gestureMode = G.NONE
    private var seekStartMs = 0L
    private var seekTargetMs = 0L
    private var startBrightness = 0.5f
    private var startVolume = 100

    private val hideRunnable = Runnable { setControls(false) }
    private val hideOverlayRunnable = Runnable { binding.gesturePanel.visibility = View.GONE }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
            val svc = (b as VideoService.LocalBinder).service
            service = svc; bound = true
            svc.listener = this@PlayerActivity
            svc.attachViews(binding.videoLayout)
            val paths = playlist.map { it.absolutePath }
            val target = playlist.getOrNull(startIndex)?.name
            if (!svc.isPlaying() || svc.currentName() != target) {
                svc.setPlaylistAndPlay(paths, startIndex)
            } else {
                onTrack(svc.currentIndex(), svc.currentName())
                onState(true)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        val (list, idx) = Playlist.siblings(file, audio = false)
        playlist = list
        startIndex = idx
        binding.audioArt.visibility = View.GONE

        setupControls()
        setupGestures()
        setControls(true)

        val svc = Intent(this, VideoService::class.java)
        ContextCompat.startForegroundService(this, svc)
        bindService(svc, connection, Context.BIND_AUTO_CREATE)
    }

    private fun shareCurrent() {
        val i = service?.currentIndex() ?: startIndex
        val f = playlist.getOrNull(i) ?: playlist.getOrNull(startIndex) ?: return
        OpenHelper.share(this, f)
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { Anim.bounce(it); shareCurrent() }
        binding.btnPlayPause.setOnClickListener { Anim.bounce(it); service?.toggle() }
        binding.btnPrev.setOnClickListener { Anim.bounce(it); service?.prev() }
        binding.btnNext.setOnClickListener { Anim.bounce(it); service?.next() }
        binding.btnAspect.setOnClickListener { cycleAspect() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnRotate.setOnClickListener { toggleRotation() }
        binding.btnSpeed.setOnClickListener { speedDialog() }

        binding.seekBar.max = 1000
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.timeCurrent.text = fmt(duration * progress / 1000L)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true; ui.removeCallbacks(hideRunnable) }
            override fun onStopTrackingTouch(sb: SeekBar) {
                userSeeking = false
                if (duration > 0) service?.seekTo(duration * sb.progress / 1000L)
                scheduleHide()
            }
        })
    }

    private fun seekRelative(deltaMs: Long) {
        val t = ((service?.currentTime() ?: 0L) + deltaMs).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        service?.seekTo(t)
        if (!controlsVisible) setControls(true) else scheduleHide()
    }

    private fun toggleMute() {
        muted = !muted
        service?.setVolume(if (muted) 0 else 100)
        binding.btnMute.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume)
        scheduleHide()
    }

    private fun cycleAspect() {
        aspectIndex = (aspectIndex + 1) % aspectModes.size
        service?.setAspect(aspectModes[aspectIndex])
        scheduleHide()
    }

    private fun toggleRotation() {
        orientationIndex = (orientationIndex + 1) % orientations.size
        requestedOrientation = orientations[orientationIndex]
        scheduleHide()
    }

    private fun speedLabel(s: Float) = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "x"

    private fun speedDialog() {
        val labels = speeds.map { speedLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setSingleChoiceItems(labels, speedIndex) { d, which ->
                speedIndex = which
                service?.setRate(speeds[which])
                binding.btnSpeed.text = speedLabel(speeds[which])
                d.dismiss(); scheduleHide()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- VideoService.Listener ----
    override fun onState(playing: Boolean) = runOnUiThread {
        binding.btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (playing) scheduleHide()
    }

    override fun onTime(pos: Long, dur: Long) = runOnUiThread {
        duration = dur
        if (!userSeeking) {
            binding.timeCurrent.text = fmt(pos)
            binding.timeTotal.text = fmt(dur)
            if (dur > 0) binding.seekBar.progress = (pos * 1000L / dur).toInt()
        }
    }

    override fun onTrack(index: Int, name: String) = runOnUiThread {
        binding.playerTitle.text = name
        binding.seekBar.progress = 0
    }

    // ---- Gestures ----
    private fun setupGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gestureMode = G.NONE
                seekStartMs = service?.currentTime() ?: 0L
                seekTargetMs = seekStartMs
                startBrightness = currentBrightness()
                startVolume = service?.volume() ?: 100
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { setControls(!controlsVisible); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (e.x < binding.root.width / 2f) seekRelative(-10_000) else seekRelative(10_000)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                if (e1 == null) return false
                val w = binding.root.width.coerceAtLeast(1)
                val h = binding.root.height.coerceAtLeast(1)
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (gestureMode == G.NONE) {
                    if (abs(dx) < 40 && abs(dy) < 40) return false
                    gestureMode = when {
                        abs(dx) > abs(dy) -> G.SEEK
                        e1.x < w / 2f -> G.BRIGHTNESS
                        else -> G.VOLUME
                    }
                }
                when (gestureMode) {
                    G.SEEK -> if (duration > 0) {
                        val deltaMs = (dx / w * duration).toLong()
                        seekTargetMs = (seekStartMs + deltaMs).coerceIn(0, duration)
                        val diff = (seekTargetMs - seekStartMs) / 1000
                        showSeek("${fmt(seekTargetMs)} / ${fmt(duration)}   ${if (diff >= 0) "+" else ""}${diff}s")
                    }
                    G.BRIGHTNESS -> applyBrightness(startBrightness + (-dy / (h * 1.5f)))
                    G.VOLUME -> applyVolume(startVolume + (-dy / (h * 1.5f) * 100f).toInt())
                    G.NONE -> {}
                }
                return true
            }
        })
        binding.root.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == G.SEEK && duration > 0) {
                    service?.seekTo(seekTargetMs)
                    if (!controlsVisible) setControls(true) else scheduleHide()
                }
                gestureMode = G.NONE
                hideOverlaySoon()
            }
            true
        }
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (e: Exception) { 0.5f }
    }

    private fun applyBrightness(v: Float) {
        val b = v.coerceIn(0.02f, 1f)
        val lp = window.attributes; lp.screenBrightness = b; window.attributes = lp
        showLevel(R.drawable.ic_brightness, (b * 100).toInt())
    }

    private fun applyVolume(v: Int) {
        val vol = v.coerceIn(0, 100)
        service?.setVolume(vol)
        muted = vol == 0
        binding.btnMute.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume)
        showLevel(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume, vol)
    }

    private fun showLevel(iconRes: Int, pct: Int) {
        ui.removeCallbacks(hideOverlayRunnable)
        binding.gestureIcon.setImageResource(iconRes)
        binding.gestureBar.visibility = View.VISIBLE
        binding.gestureBar.progress = pct
        binding.gesturePercent.text = "$pct%"
        binding.gesturePanel.visibility = View.VISIBLE
    }

    private fun showSeek(text: String) {
        ui.removeCallbacks(hideOverlayRunnable)
        binding.gestureIcon.setImageResource(R.drawable.ic_forward)
        binding.gestureBar.visibility = View.GONE
        binding.gesturePercent.text = text
        binding.gesturePanel.visibility = View.VISIBLE
    }

    private fun hideOverlaySoon() {
        ui.removeCallbacks(hideOverlayRunnable)
        ui.postDelayed(hideOverlayRunnable, 700)
    }

    private fun setControls(show: Boolean) {
        controlsVisible = show
        if (show) {
            binding.controls.visibility = View.VISIBLE
            binding.controls.animate().alpha(1f).setDuration(180).start()
            scheduleHide()
        } else {
            binding.controls.animate().alpha(0f).setDuration(180)
                .withEndAction { binding.controls.visibility = View.GONE }.start()
        }
        setSystemUi(show)
    }

    private fun scheduleHide() {
        ui.removeCallbacks(hideRunnable)
        if (service?.isPlaying() == true) ui.postDelayed(hideRunnable, 3600)
    }

    private fun setSystemUi(show: Boolean) {
        val c = WindowInsetsControllerCompat(window, binding.root)
        if (show) c.show(WindowInsetsCompat.Type.systemBars())
        else {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    override fun onStart() {
        super.onStart()
        service?.attachViews(binding.videoLayout)
    }

    override fun onStop() {
        super.onStop()
        // Release the video surface; audio keeps playing in the background.
        service?.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        if (bound) {
            service?.listener = null
            unbindService(connection)
            bound = false
        }
        // Back / finish stops playback entirely; leaving via Home keeps it in the background.
        if (isFinishing) stopService(Intent(this, VideoService::class.java))
    }
}
