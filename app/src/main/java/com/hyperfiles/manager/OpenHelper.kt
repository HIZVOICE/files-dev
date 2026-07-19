package com.hyperfiles.manager

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object OpenHelper {

    const val EXTRA_PATH = "extra_path"

    fun uriFor(activity: Activity, file: File): Uri =
        FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)

    fun mimeOf(file: File): String {
        val ext = FileTypes.ext(file.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    /** Route a tapped file to the right in-app viewer, falling back to the system chooser. */
    fun open(activity: Activity, file: File) {
        if (!file.exists()) {
            Toast.makeText(activity, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val name = file.name
        when {
            FileTypes.isImage(name) -> launch(activity, ImageViewerActivity::class.java, file)
            FileTypes.isMedia(name) -> launchPlayer(activity, file)
            FileTypes.isArchive(name) -> launchArchive(activity, file)
            FileTypes.isApk(name) -> installApk(activity, file)
            FileTypes.isDocViewable(name) -> launch(activity, DocViewerActivity::class.java, file)
            FileTypes.isText(name) -> launch(activity, TextViewerActivity::class.java, file)
            name.equals("payload.bin", true) || (FileTypes.isBinary(name) && PayloadDumper.isPayload(file)) ->
                launch(activity, PayloadActivity::class.java, file)
            FileTypes.isBinary(name) -> launch(activity, HexViewerActivity::class.java, file)
            else -> systemViewOrHex(activity, file)
        }
    }

    private fun launch(activity: Activity, cls: Class<*>, file: File) {
        activity.startActivity(Intent(activity, cls).putExtra(EXTRA_PATH, file.absolutePath))
    }

    fun launchPlayer(activity: Activity, file: File) {
        val cls = if (FileTypes.isAudio(file.name)) AudioPlayerActivity::class.java
        else PlayerActivity::class.java
        activity.startActivity(Intent(activity, cls).putExtra(EXTRA_PATH, file.absolutePath))
    }

    fun launchArchive(activity: Activity, file: File) {
        try {
            val cls = Class.forName("com.hyperfiles.manager.ArchiveActivity")
            activity.startActivity(Intent(activity, cls).putExtra(EXTRA_PATH, file.absolutePath))
        } catch (e: ClassNotFoundException) {
            systemView(activity, file)
        }
    }

    fun installApk(activity: Activity, file: File) {
        // Android 8+ requires the per-app "install unknown apps" permission.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            && !activity.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(activity, "Allow \"Install unknown apps\" for Files Dev, then tap Install again",
                Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:${activity.packageName}"))
                )
            } catch (e: Exception) {
                try { activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)) }
                catch (_: Exception) {}
            }
            return
        }
        SplitApkInstaller.install(activity, file)
    }

    fun systemView(activity: Activity, file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(activity, file), mimeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun systemViewOrHex(activity: Activity, file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(activity, file), mimeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            launch(activity, HexViewerActivity::class.java, file)
        }
    }

    fun share(activity: Activity, file: File) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(file)
                putExtra(Intent.EXTRA_STREAM, uriFor(activity, file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Share"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMultiple(activity: Activity, files: List<File>) {
        val uris = ArrayList<Uri>()
        for (f in files) if (!f.isDirectory) {
            try { uris.add(uriFor(activity, f)) } catch (_: Exception) {}
        }
        if (uris.isEmpty()) { Toast.makeText(activity, "No files to share", Toast.LENGTH_SHORT).show(); return }
        try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Share ${uris.size} files"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
