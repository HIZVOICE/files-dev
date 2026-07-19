package com.hyperfiles.manager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Best-effort embedded album-art thumbnails for audio files. Extraction runs on a
 * small background pool; results are cached and applied only if the target view has
 * not been recycled to another item (guarded via the view tag).
 */
object Thumbs {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    // Files we already know have no embedded art — skip re-scanning them.
    private val misses = Collections.synchronizedSet(HashSet<String>())

    /**
     * Loads cover art for the media notification off the main thread: embedded art for
     * audio, a first frame for video. Delivers on the main thread (null if none).
     */
    fun loadArt(path: String, video: Boolean, cb: (Bitmap?) -> Unit) {
        cache.get("art:$path")?.let { cb(it); return }
        pool.execute {
            var bmp: Bitmap? = null
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(path)
                val art = mmr.embeddedPicture
                if (art != null && art.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    bmp = BitmapFactory.decodeByteArray(art, 0, art.size, opts)
                }
                if (bmp == null && video) {
                    bmp = mmr.getFrameAtTime(0)
                }
            } catch (e: Exception) {
            } finally {
                try { mmr.release() } catch (e: Exception) {}
            }
            if (bmp != null) cache.put("art:$path", bmp)
            val result = bmp
            main.post { cb(result) }
        }
    }

    fun loadAudioArt(view: ImageView, file: File, fallbackRes: Int) {
        val key = file.absolutePath
        view.tag = key

        fun showArt(bmp: Bitmap) {
            view.setPadding(0, 0, 0, 0)
            view.scaleType = ImageView.ScaleType.CENTER_CROP
            view.setImageBitmap(bmp)
        }
        fun showGlyph() {
            val p = (view.resources.displayMetrics.density * 15).toInt()
            view.setPadding(p, p, p, p)
            view.scaleType = ImageView.ScaleType.FIT_CENTER
            view.setImageResource(fallbackRes)
        }

        val cached = cache.get(key)
        if (cached != null) { showArt(cached); return }
        showGlyph()
        if (misses.contains(key)) return

        pool.execute {
            var bmp: Bitmap? = null
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val art = mmr.embeddedPicture
                if (art != null && art.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    bmp = BitmapFactory.decodeByteArray(art, 0, art.size, opts)
                }
            } catch (e: Exception) {
                // unreadable / no metadata — treat as a miss
            } finally {
                try { mmr.release() } catch (e: Exception) {}
            }
            if (bmp != null) cache.put(key, bmp) else misses.add(key)
            val result = bmp
            main.post {
                if (view.tag == key && result != null) showArt(result)
            }
        }
    }
}
