package com.hyperfiles.manager

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Installs single APKs and split-APK bundles (.xapk / .apks / .apkm) using the
 * PackageInstaller session API, writing every contained .apk as a split.
 */
object SplitApkInstaller {

    fun install(activity: Activity, file: File) {
        val ext = FileTypes.ext(file.name)
        if (ext == "aab") {
            Toast.makeText(activity,
                "AAB is a Google Play bundle and cannot be installed directly. Convert it with bundletool first.",
                Toast.LENGTH_LONG).show()
            return
        }
        val dialog = android.app.AlertDialog.Builder(activity)
            .setMessage("Preparing install…").setCancelable(false).show()
        Thread {
            val error = try {
                doInstall(activity, file, ext)
            } catch (e: Exception) {
                "Install error: ${e.message}"
            }
            activity.runOnUiThread {
                dialog.dismiss()
                if (error != null) Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                // success path continues via InstallResultReceiver (user confirmation)
            }
        }.start()
    }

    /** Returns an error string, or null if the session was committed (result via receiver). */
    private fun doInstall(activity: Activity, file: File, ext: String): String? {
        val pi = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { session ->
            if (ext == "apk") {
                file.inputStream().use { writeSplit(session, "base.apk", it, file.length()) }
            } else {
                var count = 0
                ZipFile(file).use { zf ->
                    val entries = zf.entries().toList().filter { it.name.endsWith(".apk", true) }
                    if (entries.isEmpty()) return "No APK found inside ${file.name}"
                    for (e in entries) {
                        zf.getInputStream(e).use { input ->
                            writeSplit(session, "split_$count.apk", input, if (e.size >= 0) e.size else -1)
                        }
                        count++
                    }
                }
                copyObbIfAny(file)
            }
            val statusIntent = Intent(activity, InstallResultReceiver::class.java)
                .setAction("com.hyperfiles.manager.INSTALL_STATUS")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getBroadcast(activity, sessionId, statusIntent, flags)
            session.commit(pending.intentSender)
        }
        return null
    }

    private fun writeSplit(session: PackageInstaller.Session, name: String, input: InputStream, len: Long) {
        session.openWrite(name, 0, len).use { out ->
            input.copyTo(out)
            session.fsync(out)
        }
    }

    /** Best-effort: copy any .obb from an XAPK into Android/obb/<package>/. */
    private fun copyObbIfAny(file: File) {
        try {
            ZipFile(file).use { zf ->
                val obbEntries = zf.entries().toList().filter { it.name.endsWith(".obb", true) }
                if (obbEntries.isEmpty()) return
                var pkg: String? = null
                zf.getEntry("manifest.json")?.let { m ->
                    val json = zf.getInputStream(m).use { it.readBytes().toString(Charsets.UTF_8) }
                    pkg = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                }
                if (pkg == null) {
                    pkg = obbEntries.firstNotNullOfOrNull {
                        Regex("Android/obb/([^/]+)/").find(it.name)?.groupValues?.get(1)
                    }
                }
                val p = pkg ?: return
                val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/$p").apply { mkdirs() }
                for (e in obbEntries) {
                    val outName = e.name.substringAfterLast('/')
                    zf.getInputStream(e).use { input ->
                        File(obbDir, outName).outputStream().use { input.copyTo(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
