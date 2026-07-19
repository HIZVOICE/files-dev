package com.hyperfiles.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import java.io.File

class PlaybackService : Service() {

    companion object {
        const val CHANNEL = "playback"
        const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "com.hyperfiles.manager.TOGGLE"
        const val ACTION_NEXT = "com.hyperfiles.manager.NEXT"
        const val ACTION_PREV = "com.hyperfiles.manager.PREV"
        const val ACTION_STOP = "com.hyperfiles.manager.STOP"
    }

    interface Listener {
        fun onState(playing: Boolean)
        fun onTime(pos: Long, dur: Long)
        fun onTrack(index: Int, name: String)
    }

    inner class LocalBinder : Binder() { val service get() = this@PlaybackService }
    private val binder = LocalBinder()

    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    private var playlist: List<String> = emptyList()
    private var index = 0
    private var duration = 0L
    private var startedForeground = false
    private var session: MediaSessionCompat? = null
    private var art: Bitmap? = null
    private var lastStateTs = 0L
    var listener: Listener? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "FilesDevAudio").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onStop() { stopSelf() }
                override fun onSeekTo(pos: Long) { seekTo(pos); updatePlaybackState() }
            })
            isActive = true
        }
        libVLC = LibVLC(this, arrayListOf("--no-video", "--audio-time-stretch"))
        player = MediaPlayer(libVLC).also { mp ->
            mp.setEventListener { e -> onEvent(e) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startedForeground) {
            startForegroundNow(false)
        }
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> prev()
            ACTION_STOP -> { stopSelf(); }
        }
        return START_NOT_STICKY
    }

    // ---- Public control ----
    fun setPlaylistAndPlay(list: List<String>, startIndex: Int) {
        playlist = if (list.isEmpty()) emptyList() else list
        index = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        playCurrent()
    }

    fun toggle() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause() else mp.play()
    }

    fun next() { if (playlist.isNotEmpty()) { index = (index + 1) % playlist.size; playCurrent() } }
    fun prev() { if (playlist.isNotEmpty()) { index = (index - 1 + playlist.size) % playlist.size; playCurrent() } }
    fun seekTo(pos: Long) { player?.time = pos }
    fun isPlaying() = player?.isPlaying == true
    fun currentName() = playlist.getOrNull(index)?.let { File(it).name } ?: ""
    fun currentIndex() = index
    fun currentDuration() = duration

    private fun playCurrent() {
        val mp = player ?: return
        val path = playlist.getOrNull(index) ?: return
        try {
            val media = Media(libVLC, Uri.fromFile(File(path)))
            mp.media = media
            media.release()
            mp.play()
            art = null
            Thumbs.loadArt(path, video = false) { bmp ->
                if (playlist.getOrNull(index) == path) { art = bmp; updateMetadata(); updateNotification() }
            }
            ui.post { listener?.onTrack(index, File(path).name) }
            updateMetadata()
            updateNotification()
        } catch (e: Exception) {
            // skip broken track
        }
    }

    private fun onEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.EndReached -> ui.post { next() }
            MediaPlayer.Event.LengthChanged -> { duration = e.lengthChanged; updateMetadata() }
            MediaPlayer.Event.Playing, MediaPlayer.Event.Paused -> {
                val playing = player?.isPlaying == true
                ui.post { listener?.onState(playing) }
                updatePlaybackState()
                updateNotification()
            }
            MediaPlayer.Event.TimeChanged -> {
                val t = e.timeChanged
                ui.post { listener?.onTime(t, duration) }
                if (t - lastStateTs > 1000 || t < lastStateTs) { lastStateTs = t; updatePlaybackState() }
            }
        }
    }

    private fun updateMetadata() {
        val mb = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentName().ifEmpty { "Files Dev" })
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
                    NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun buildNotification(playing: Boolean): Notification {
        val contentPI = PendingIntent.getActivity(
            this, 0, Intent(this, AudioPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags()
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(servicePI(ACTION_STOP))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(currentName().ifEmpty { "Files Dev" })
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

    private fun startForegroundNow(playing: Boolean) {
        val notif = buildNotification(playing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        startedForeground = true
    }

    private fun updateNotification() {
        val playing = player?.isPlaying == true
        if (!startedForeground) { startForegroundNow(playing); return }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(playing))
    }

    private fun servicePI(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action), piFlags())

    private fun piFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        session?.let { it.isActive = false; it.release() }
        session = null
        player?.let { it.setEventListener(null); it.stop(); it.release() }
        player = null
        libVLC?.release(); libVLC = null
        listener = null
    }
}
