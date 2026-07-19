package com.hyperfiles.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Ongoing progress notifications for long file operations.
 *
 * On Android 16 (API 36)+ this posts a real Notification.ProgressStyle notification
 * flagged FLAG_PROMOTED_ONGOING, so it appears as a Live Update on the status bar,
 * lock screen and Samsung One UI "Now Bar". Below 36 it falls back to a
 * NotificationCompat CATEGORY_PROGRESS ongoing notification.
 */
object LiveNotify {

    const val CHANNEL = "operations"

    private fun enabled(c: Context): Boolean =
        Prefs.liveNotify(c) && NotificationManagerCompat.from(c).areNotificationsEnabled()

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun post(c: Context, id: Int, title: String, text: String, progress: Int, max: Int) {
        if (!enabled(c)) return
        ensureChannel(c)
        if (Build.VERSION.SDK_INT >= 36) postPromoted(c, id, title, text, progress, max)
        else postCompat(c, id, title, text, progress, max)
    }

    @RequiresApi(36)
    private fun postPromoted(c: Context, id: Int, title: String, text: String, progress: Int, max: Int) {
        val total = if (max <= 0) 100 else max
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total)))
            .setProgress(if (max <= 0) 0 else progress.coerceIn(0, total))
            .setProgressIndeterminate(max <= 0)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(style)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        n.flags = n.flags or Notification.FLAG_PROMOTED_ONGOING
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun postCompat(c: Context, id: Int, title: String, text: String, progress: Int, max: Int) {
        val b = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(if (max <= 0) 0 else max, progress.coerceIn(0, if (max <= 0) 0 else max), max <= 0)
        try { NotificationManagerCompat.from(c).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    fun start(c: Context, id: Int, title: String, max: Int = 0) = post(c, id, title, "Working…", 0, max)

    fun update(c: Context, id: Int, title: String, progress: Int, max: Int) =
        post(c, id, title, "$progress / $max", progress, max)

    fun finish(c: Context, id: Int) {
        try { NotificationManagerCompat.from(c).cancel(id) } catch (_: Exception) {}
    }
}
