package com.hyperfiles.manager

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import java.io.File

/**
 * Android/data and Android/obb are OS-restricted and can't be listed with the
 * File API on Android 11+. For those we hand off to the Storage Access Framework
 * document-tree picker instead.
 */
object SafHelper {

    fun isRestricted(file: File): Boolean {
        val p = file.absolutePath
        return p.endsWith("/Android/data") || p.endsWith("/Android/obb") ||
                p.contains("/Android/data/") || p.contains("/Android/obb/")
    }

    fun openForRestricted(activity: Activity, file: File) {
        val sub = if (file.absolutePath.contains("/Android/obb")) "obb" else "data"
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Android/$sub"
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            } catch (_: Exception) {}
        }
        try {
            activity.startActivity(intent)
            Toast.makeText(activity,
                "Android/$sub is protected — use the system picker to grant access",
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Storage Access Framework unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}
