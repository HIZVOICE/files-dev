package com.hyperfiles.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/** Remembers per-video resume positions. */
object ResumeStore {
    private fun p(c: Context) = c.getSharedPreferences("resume", Context.MODE_PRIVATE)
    private fun key(path: String) = "r_" + path.hashCode()
    fun save(c: Context, path: String, ms: Long) {
        if (ms > 3000) p(c).edit().putLong(key(path), ms).apply() else clear(c, path)
    }
    fun get(c: Context, path: String): Long = p(c).getLong(key(path), 0L)
    fun clear(c: Context, path: String) = p(c).edit().remove(key(path)).apply()
}

class VideoService : Service() {

    companion object {
        const val CHANNEL = "video"
        const val NOTIF_ID = 43
        const val ACTION_TOGGLE = "com.hyperfiles.manager.V_TOGGLE"
        const val ACTION_NEXT = "com.hyperfiles.manager.V_NEXT"
        const val ACTION_PREV = "com.hyperfiles.manager.V_PREV"
        const val ACTION_STOP = "com.hyperfiles.manager.V_STOP"
    }

    interface Listener {
        fun onState(playing: Boolean)
        fun onTime(pos: Long, dur: Long)
        fun onTrack(index: Int, name: String)
    }

    inner class LocalBinder : Binder() { val service get() = this@VideoService }
    private val binder = LocalBinder()

    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    private var playlist: List<String> = emptyList()
    private var index = 0
    private var duration = 0L
    private var pendingResume = 0L
    private var lastSave = 0L
    private var startedForeground = false
    private var session: MediaSessionCompat? = null
    private var art: Bitmap? = null
    private var lastStateTs = 0L
    var listener: Listener? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "FilesDevVideo").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onStop() { savePosition(); stopSelf() }
                override fun onSeekTo(pos: Long) { seekTo(pos); updatePlaybackState() }
            })
            isActive = true
        }
        libVLC = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--audio-time-stretch"))
        player = MediaPlayer(libVLC).also { it.setEventListener { e -> onEvent(e) } }
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startedForeground) startForegroundNow()
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> prev()
            ACTION_STOP -> { savePosition(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    // ---- View attach/detach (video surface) ----
    fun attachViews(layout: VLCVideoLayout) {
        try { player?.attachViews(layout, null, true, false) } catch (_: Exception) {}
    }
    fun detachViews() {
        try { player?.detachViews() } catch (_: Exception) {}
    }

    // ---- Control ----
    fun setPlaylistAndPlay(list: List<String>, startIndex: Int) {
        playlist = list
        index = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        playCurrent()
    }

    fun toggle() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { if (playlist.isNotEmpty()) { savePosition(); index = (index + 1) % playlist.size; playCurrent() } }
    fun prev() { if (playlist.isNotEmpty()) { savePosition(); index = (index - 1 + playlist.size) % playlist.size; playCurrent() } }
    fun seekTo(ms: Long) { player?.time = ms.coerceAtLeast(0) }
    fun setRate(r: Float) { player?.setRate(r) }
    fun setAspect(mode: String?) { player?.setAspectRatio(mode); player?.setScale(0f) }
    fun setVolume(v: Int) { player?.volume = v.coerceIn(0, 100) }
    fun volume() = player?.volume ?: 100
    fun isPlaying() = player?.isPlaying == true
    fun currentTime() = player?.time ?: 0L
    fun currentDuration() = duration
    fun currentName() = playlist.getOrNull(index)?.let { File(it).name } ?: ""
    fun currentIndex() = index
    fun currentPath() = playlist.getOrNull(index)

    private fun playCurrent() {
        val mp = player ?: return
        val path = playlist.getOrNull(index) ?: return
        try {
            mp.stop()
            val media = Media(libVLC, Uri.fromFile(File(path))).apply { setHWDecoderEnabled(true, false) }
            mp.media = media
            media.release()
            mp.play()
            pendingResume = ResumeStore.get(this, path)
            art = null
            Thumbs.loadArt(path, video = true) { bmp ->
                if (currentPath() == path) { art = bmp; updateMetadata(); updateNotification() }
            }
            ui.post { listener?.onTrack(index, File(path).name) }
            updateMetadata()
            updateNotification()
        } catch (_: Exception) {}
    }

    private fun savePosition() {
        val path = currentPath() ?: return
        val t = player?.time ?: 0L
        if (t in 3001 until (duration - 3000).coerceAtLeast(3001)) ResumeStore.save(this, path, t)
        else ResumeStore.clear(this, path)
    }

    private fun onEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.Playing -> {
                ui.post { listener?.onState(true) }
                if (pendingResume > 0) { player?.time = pendingResume; pendingResume = 0 }
                updatePlaybackState(); updateNotification()
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                ui.post { listener?.onState(false) }
                savePosition(); updatePlaybackState(); updateNotification()
            }
            MediaPlayer.Event.LengthChanged -> {
                duration = e.lengthChanged
                if (pendingResume > 0) { player?.time = pendingResume; pendingResume = 0 }
                updateMetadata()
            }
            MediaPlayer.Event.EndReached -> {
                currentPath()?.let { ResumeStore.clear(this, it) }
                ui.post { if (playlist.size > 1) next() else listener?.onState(false) }
            }
            MediaPlayer.Event.TimeChanged -> {
                val t = e.timeChanged
                ui.post { listener?.onTime(t, duration) }
                if (t - lastSave > 5000) { lastSave = t; savePosition() }
                if (t - lastStateTs > 1000 || t < lastStateTs) { lastStateTs = t; updatePlaybackState() }
            }
        }
    }

    private fun updateMetadata() {
        val mb = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentName().ifEmpty { "Video" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Files Dev")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        art?.let { mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        session?.setMetadata(mb.build())
    }

    private fun updatePlaybackState() {
        val playing = player?.isPlaying == true
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val pb = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, (player?.time ?: 0L), if (playing) 1f else 0f)
            .build()
        session?.setPlaybackState(pb)
    }

    // ---- Notification ----
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Video playback", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) })
            }
        }
    }

    private fun buildNotification(): Notification {
        val playing = isPlaying()
        val contentPI = PendingIntent.getActivity(
            this, 0, Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            piFlags()
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(servicePI(ACTION_STOP))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_video)
            .setContentTitle(currentName().ifEmpty { "Video" })
            .setContentText("Files Dev player")
            .setContentIntent(contentPI)
            .setLargeIcon(art)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_prev, "Previous", servicePI(ACTION_PREV))
            .addAction(if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play", servicePI(ACTION_TOGGLE))
            .addAction(R.drawable.ic_next, "Next", servicePI(ACTION_NEXT))
            .setStyle(style)
            .build()
    }

    private fun startForegroundNow() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIF_ID, notif)
        startedForeground = true
    }

    private fun updateNotification() {
        if (!startedForeground) { startForegroundNow(); return }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun servicePI(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(),
            Intent(this, VideoService::class.java).setAction(action), piFlags())

    private fun piFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

    override fun onDestroy() {
        super.onDestroy()
        savePosition()
        ui.removeCallbacksAndMessages(null)
        session?.let { it.isActive = false; it.release() }
        session = null
        player?.let { it.setEventListener(null); it.stop(); it.detachViews(); it.release() }
        player = null
        libVLC?.release(); libVLC = null
        listener = null
    }
}
