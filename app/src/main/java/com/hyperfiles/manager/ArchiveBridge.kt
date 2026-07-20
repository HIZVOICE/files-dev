package com.hyperfiles.manager

import android.app.Activity
import android.app.ProgressDialog
import android.widget.Toast
import java.io.File

/**
 * Entry point for archive actions used by the file browser context menu.
 * Extraction/creation route through ArchiveEngine (Commons Compress + stdlib ZIP),
 * reading restricted sources via the elevated backend and writing to a
 * guaranteed-writable location.
 */
object ArchiveBridge {

    fun compressZip(activity: Activity, files: List<File>, onChanged: () -> Unit) {
        if (files.isEmpty()) return
        val out = uniqueFile(pickOutputDir(files[0].parentFile), archiveBaseName(files) + ".zip")
        val dialog = progress(activity, "Compressing…")
        LiveNotify.start(activity, 4203, "Creating ${out.name}")
        Thread {
            val result = runCatching { ZipUtil.zip(files, out); out }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4203); dialog.dismiss()
                result.onSuccess { FileScanner.invalidate(); onChanged(); toast(activity, "Created ${it.name}") }
                    .onFailure { toast(activity, "Compress failed: ${it.message ?: it.javaClass.simpleName}") }
            }
        }.start()
    }

    fun extract(activity: Activity, archive: File, destParent: File, onChanged: () -> Unit) {
        if (!ArchiveEngine.isSupported(archive)) { toast(activity, "Unsupported archive format"); return }
        val dialog = progress(activity, "Extracting…")
        LiveNotify.start(activity, 4202, "Extracting ${archive.name}")
        Thread {
            val result = runCatching {
                val src = Readable.resolve(archive) ?: throw IllegalStateException(Readable.reason())
                val target = ArchiveEngine.writableExtractDir(destParent, ArchiveEngine.baseName(archive))
                ArchiveEngine.extractAll(src, target); target
            }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4202); dialog.dismiss()
                result.onSuccess { FileScanner.invalidate(); onChanged(); toast(activity, "Extracted to ${it.absolutePath}") }
                    .onFailure { toast(activity, "Extract failed: ${it.message ?: it.javaClass.simpleName}") }
            }
        }.start()
    }

    fun compress7z(activity: Activity, files: List<File>, onChanged: () -> Unit) {
        if (files.isEmpty()) return
        val out = uniqueFile(pickOutputDir(files[0].parentFile), archiveBaseName(files) + ".7z")
        val dialog = progress(activity, "Compressing…")
        LiveNotify.start(activity, 4204, "Creating ${out.name}")
        Thread {
            val result = runCatching { ArchiveEngine.create7z(files, out); out }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4204); dialog.dismiss()
                result.onSuccess { FileScanner.invalidate(); onChanged(); toast(activity, "Created ${it.name}") }
                    .onFailure { toast(activity, "Compress failed: ${it.message ?: it.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun archiveBaseName(files: List<File>): String =
        if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name }
        else (files[0].parentFile?.name ?: "").ifEmpty { "archive" }

    /** Write output next to the source when possible, else Download/FilesDev (always writable). */
    private fun pickOutputDir(parent: File?): File =
        if (parent != null && parent.isDirectory && parent.canWrite() && !SafHelper.isRestricted(parent)) parent
        else File(StorageUtil.primaryStorage(), "Download/FilesDev").apply { mkdirs() }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }

    @Suppress("DEPRECATION")
    private fun progress(activity: Activity, msg: String): ProgressDialog =
        ProgressDialog(activity).apply {
            setMessage(msg); setCancelable(false); show()
        }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
}
