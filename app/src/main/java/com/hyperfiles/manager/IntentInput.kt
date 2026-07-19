package com.hyperfiles.manager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves the file a viewer/player should open. Supports:
 *  - internal launches carrying EXTRA_PATH,
 *  - external VIEW/SEND intents with a file:// or content:// Uri.
 * content:// sources are copied into the app cache so the rest of the app can
 * work with a plain File.
 */
object IntentInput {

    fun resolveToFile(activity: Activity, intent: Intent): File? {
        intent.getStringExtra(OpenHelper.EXTRA_PATH)?.let { return File(it) }
        val uri = intent.data ?: (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri) ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> copyToCache(activity, uri)
            else -> uri.path?.let { File(it) }
        }
    }

    private fun copyToCache(activity: Activity, uri: Uri): File? = try {
        val name = queryName(activity, uri) ?: "shared_${System.currentTimeMillis()}"
        val dir = File(activity.cacheDir, "incoming").apply { mkdirs() }
        val out = File(dir, name)
        activity.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        }
        out
    } catch (e: Exception) {
        null
    }

    private fun queryName(activity: Activity, uri: Uri): String? {
        return try {
            activity.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
