package com.hyperfiles.manager

import android.app.Activity
import android.app.ProgressDialog
import android.widget.Toast
import java.io.File

/**
 * Entry point for archive actions used by the file browser context menu.
 * ZIP is handled here directly with the stdlib. Other formats (7z, tar, gz,
 * bz2, xz) are handled by ArchiveEngine (Commons Compress) once present; this
 * bridge delegates to it via ArchiveActivity for browsing and extraction.
 */
object ArchiveBridge {

    fun compressZip(activity: Activity, files: List<File>, onChanged: () -> Unit) {
        if (files.isEmpty()) return
        val base = files[0].parentFile ?: return
        val out = uniqueFile(base, archiveBaseName(files) + ".zip")
        val dialog = progress(activity, "Compressing…")
        LiveNotify.start(activity, 4203, "Creating ${out.name}")
        Thread {
            val ok = try { ZipUtil.zip(files, out); true } catch (e: Exception) { false }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4203)
                dialog.dismiss()
                if (ok) { FileScanner.invalidate(); onChanged(); toast(activity, "Created ${out.name}") }
                else toast(activity, "Compress failed")
            }
        }.start()
    }

    fun extract(activity: Activity, archive: File, destParent: File, onChanged: () -> Unit) {
        val name = archive.name.lowercase()
        val isZip = name.endsWith(".zip") || name.endsWith(".jar")
        if (!isZip && !ArchiveEngine.isSupported(archive)) {
            toast(activity, "Unsupported archive format")
            return
        }
        val target = uniqueDir(destParent, baseName(archive))
        val dialog = progress(activity, "Extracting…")
        LiveNotify.start(activity, 4202, "Extracting ${archive.name}")
        Thread {
            val ok = try {
                if (isZip) ZipUtil.unzip(archive, target) else ArchiveEngine.extractAll(archive, target)
                true
            } catch (e: Exception) { false }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4202)
                dialog.dismiss()
                if (ok) { FileScanner.invalidate(); onChanged(); toast(activity, "Extracted to ${target.name}") }
                else toast(activity, "Extract failed")
            }
        }.start()
    }

    fun compress7z(activity: Activity, files: List<File>, onChanged: () -> Unit) {
        if (files.isEmpty()) return
        val base = files[0].parentFile ?: return
        val out = uniqueFile(base, archiveBaseName(files) + ".7z")
        val dialog = progress(activity, "Compressing…")
        LiveNotify.start(activity, 4204, "Creating ${out.name}")
        Thread {
            val ok = try { ArchiveEngine.create7z(files, out); true } catch (e: Exception) { false }
            activity.runOnUiThread {
                LiveNotify.finish(activity, 4204)
                dialog.dismiss()
                if (ok) { FileScanner.invalidate(); onChanged(); toast(activity, "Created ${out.name}") }
                else toast(activity, "Compress failed")
            }
        }.start()
    }

    private fun archiveBaseName(files: List<File>): String =
        if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name }
        else (files[0].parentFile?.name ?: "").ifEmpty { "archive" }

    private fun baseName(f: File): String {
        val n = f.name
        val lower = n.lowercase()
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz"))
            if (lower.endsWith(s)) return n.substring(0, n.length - s.length)
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else n
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }

    private fun uniqueDir(parent: File, name: String): File {
        var f = File(parent, name)
        var i = 1
        while (f.exists()) { f = File(parent, "$name ($i)"); i++ }
        return f
    }

    @Suppress("DEPRECATION")
    private fun progress(activity: Activity, msg: String): ProgressDialog =
        ProgressDialog(activity).apply {
            setMessage(msg); setCancelable(false); show()
        }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
}
