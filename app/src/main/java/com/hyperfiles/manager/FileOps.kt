package com.hyperfiles.manager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Session clipboard for copy/move — holds one or many files. */
object Clipboard {
    var files: List<File> = emptyList()
    var move: Boolean = false
    fun set(list: List<File>, moving: Boolean) { files = list; move = moving }
    fun clear() { files = emptyList(); move = false }
    fun has() = files.isNotEmpty()
    fun count() = files.size
}

object FileOps {

    fun showMenu(activity: Activity, file: File, anchor: View, onChanged: () -> Unit) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add("Open")
        popup.menu.add("Share")
        popup.menu.add("Copy")
        popup.menu.add("Move")
        popup.menu.add("Rename")
        popup.menu.add("Delete")
        if (FileTypes.isArchive(file.name)) popup.menu.add("Extract here")
        popup.menu.add("Compress to ZIP")
        popup.menu.add("Compress to 7z")
        if (!file.isDirectory) popup.menu.add("Checksum")
        if (FileTypes.isApk(file.name)) popup.menu.add("Inspect APK")
        if (RootShell.suBinaryPresent()) {
            popup.menu.add("chmod")
            popup.menu.add("chown")
            popup.menu.add("Symlink")
        }
        if (!file.isDirectory && file.name.endsWith(".bin", true)) popup.menu.add("Open as ROM payload")
        popup.menu.add("New folder")
        popup.menu.add("Move to secure folder")
        popup.menu.add("Details")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Open" -> OpenHelper.open(activity, file)
                "Share" -> OpenHelper.share(activity, file)
                "Copy" -> { Clipboard.set(listOf(file), false); toast(activity, "Copied. Use Paste in a folder.") }
                "Move" -> { Clipboard.set(listOf(file), true); toast(activity, "Cut. Use Paste in a folder.") }
                "Rename" -> renameDialog(activity, file, onChanged)
                "Delete" -> deleteDialog(activity, file, onChanged)
                "Extract here" -> ArchiveBridge.extract(activity, file, file.parentFile ?: file, onChanged)
                "Compress to ZIP" -> ArchiveBridge.compressZip(activity, listOf(file), onChanged)
                "Compress to 7z" -> ArchiveBridge.compress7z(activity, listOf(file), onChanged)
                "Checksum" -> checksumDialog(activity, file)
                "Inspect APK" -> activity.startActivity(
                    android.content.Intent(activity, ApkInfoActivity::class.java)
                        .putExtra(OpenHelper.EXTRA_PATH, file.absolutePath))
                "chmod" -> RootOps.chmodDialog(activity, file, onChanged)
                "chown" -> RootOps.chownDialog(activity, file, onChanged)
                "Symlink" -> RootOps.symlinkDialog(activity, file, onChanged)
                "Open as ROM payload" -> activity.startActivity(
                    android.content.Intent(activity, PayloadActivity::class.java)
                        .putExtra(OpenHelper.EXTRA_PATH, file.absolutePath))
                "New folder" -> newFolderDialog(activity, file.parentFile ?: file, onChanged)
                "Move to secure folder" -> moveToSecure(activity, file, onChanged)
                "Details" -> detailsDialog(activity, file)
            }
            true
        }
        popup.show()
    }

    /** Public entry for the toolbar / header "new folder" button. */
    fun newFolder(activity: Activity, dir: File, onChanged: () -> Unit) = newFolderDialog(activity, dir, onChanged)

    private fun newFolderDialog(activity: Activity, dir: File, onChanged: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Folder name"
        }
        AlertDialog.Builder(activity)
            .setTitle("New folder")
            .setMessage("Create in ${dir.name}")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isEmpty()) { toast(activity, "Name required"); return@setPositiveButton }
                val target = File(dir, n)
                val useElev = Elevated.available() && (SafHelper.isRestricted(dir) || !dir.canWrite())
                when {
                    target.exists() -> toast(activity, "Already exists")
                    useElev -> bg(activity, { Elevated.mkdir(target) }) { ok ->
                        if (ok) { FileScanner.invalidate(); onChanged(); toast(activity, "Created $n") }
                        else toast(activity, "Couldn't create folder")
                    }
                    target.mkdirs() -> { FileScanner.invalidate(); onChanged(); toast(activity, "Created $n") }
                    else -> toast(activity, "Couldn't create folder")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameDialog(activity: Activity, file: File, onChanged: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(file.name)
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val dest = File(file.parentFile, newName)
                    if (Elevated.needed(file)) {
                        bg(activity, { Elevated.rename(file, dest) }) { ok ->
                            if (ok) { FileScanner.invalidate(); onChanged() } else toast(activity, "Rename failed")
                        }
                    } else if (file.renameTo(dest)) { FileScanner.invalidate(); onChanged() }
                    else toast(activity, "Rename failed")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDialog(activity: Activity, file: File, onChanged: () -> Unit) {
        // Restricted paths (Android/data) can't be moved to the app's recycle bin —
        // delete them permanently via the elevated shell instead.
        val elev = Elevated.needed(file)
        val bin = Prefs.useRecycleBin(activity) && !elev
        AlertDialog.Builder(activity)
            .setTitle(if (bin) "Move to Recycle bin" else "Delete")
            .setMessage(if (bin) "Move \"${file.name}\" to the Recycle bin? You can restore it later."
            else "Permanently delete \"${file.name}\"? This cannot be undone.")
            .setPositiveButton(if (bin) "Move" else "Delete") { _, _ ->
                if (elev) {
                    bg(activity, { Elevated.delete(file) }) { ok ->
                        if (ok) { FileScanner.invalidate(); onChanged() } else toast(activity, "Delete failed")
                    }
                } else {
                    val ok = if (bin) TrashBin.trash(activity, file)
                    else if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (ok) { FileScanner.invalidate(); onChanged() }
                    else toast(activity, if (bin) "Couldn't move to bin" else "Delete failed")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun detailsDialog(activity: Activity, file: File) {
        val sizeStr = if (file.isDirectory) "${file.listFiles()?.size ?: 0} items"
        else StorageUtil.formatSize(file.length())
        val perm = StorageUtil.permString(file)
        val octal = StorageUtil.permOctal(file)
        val permLine = if (perm.isNotEmpty()) "\n\nPermissions: $perm  ($octal)" else ""
        val msg = "Name: ${file.name}\n\nPath: ${file.absolutePath}\n\nSize: $sizeStr\n\n" +
                "Modified: ${StorageUtil.formatDate(file.lastModified())}$permLine\n\n" +
                "Readable: ${file.canRead()}  Writable: ${file.canWrite()}"
        AlertDialog.Builder(activity).setTitle("Details").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    fun paste(activity: Activity, targetDir: File, scope: CoroutineScope, onDone: () -> Unit) {
        val src = Clipboard.files
        if (src.isEmpty()) return
        val moving = Clipboard.move
        val verb = if (moving) "Moving" else "Copying"
        val notifId = 4201
        val progress = AlertDialog.Builder(activity)
            .setMessage("$verb ${src.size}…")
            .setCancelable(false).show()
        LiveNotify.start(activity, notifId, "$verb files", src.size)
        scope.launch {
            val failed = withContext(Dispatchers.IO) {
                var fails = 0
                src.forEachIndexed { i, f ->
                    LiveNotify.update(activity, notifId, "$verb ${f.name}", i, src.size)
                    try {
                        val dest = File(targetDir, f.name)
                        if (dest.absolutePath != f.absolutePath) {
                            if (Elevated.needed(targetDir) || Elevated.needed(f)) {
                                // One or both endpoints are restricted (Android/data) — use the shell.
                                val ok = if (moving) Elevated.moveInto(f, targetDir)
                                else Elevated.copyInto(f, targetDir)
                                if (!ok) fails++
                            } else if (!(moving && f.renameTo(dest))) {
                                val copied = f.copyRecursively(dest, overwrite = false)
                                if (copied && moving) f.deleteRecursively()
                                if (!copied) fails++
                            }
                        }
                    } catch (e: Exception) { fails++ }
                }
                fails
            }
            LiveNotify.finish(activity, notifId)
            progress.dismiss()
            Clipboard.clear(); FileScanner.invalidate(); onDone()
            toast(activity, if (failed == 0) "Done" else "$failed item(s) failed")
        }
    }

    fun deleteMultiple(activity: Activity, files: List<File>, onChanged: () -> Unit) {
        if (files.isEmpty()) return
        val bin = Prefs.useRecycleBin(activity)
        AlertDialog.Builder(activity)
            .setTitle(if (bin) "Move ${files.size} to Recycle bin" else "Delete ${files.size} item(s)")
            .setMessage(if (bin) "You can restore them later." else "This cannot be undone.")
            .setPositiveButton(if (bin) "Move" else "Delete") { _, _ ->
                Thread {
                    var fails = 0
                    for (f in files) {
                        val ok = when {
                            Elevated.needed(f) -> Elevated.delete(f)   // restricted → shell rm (permanent)
                            bin -> TrashBin.trash(activity, f)
                            f.isDirectory -> f.deleteRecursively()
                            else -> f.delete()
                        }
                        if (!ok) fails++
                    }
                    activity.runOnUiThread {
                        FileScanner.invalidate(); onChanged()
                        toast(activity, if (fails == 0) (if (bin) "Moved to bin" else "Deleted") else "$fails item(s) failed")
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun checksumMultiple(activity: Activity, files: List<File>) {
        val targets = files.filter { !it.isDirectory }
        if (targets.isEmpty()) { toast(activity, "No files selected"); return }
        val working = AlertDialog.Builder(activity)
            .setMessage("Computing ${targets.size} checksum(s)…").setCancelable(false).create()
        working.show()
        Thread {
            val sb = StringBuilder()
            for (f in targets) {
                val sha = try { computeHashes(f).third } catch (e: Exception) { "error" }
                sb.append("${f.name}\n$sha\n\n")
            }
            val text = sb.toString().trimEnd()
            activity.runOnUiThread {
                working.dismiss()
                AlertDialog.Builder(activity)
                    .setTitle("SHA-256 · ${targets.size} files")
                    .setMessage(text)
                    .setPositiveButton("Copy all") { _, _ ->
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("checksums", text))
                        toast(activity, "Copied")
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }.start()
    }

    private fun moveToSecure(activity: Activity, file: File, onChanged: () -> Unit) {
        val dir = File(activity.filesDir, "secure").apply { mkdirs() }
        Thread {
            val ok = try {
                var dest = File(dir, file.name)
                var i = 1
                while (dest.exists()) { dest = File(dir, "${file.nameWithoutExtension} ($i).${file.extension}"); i++ }
                if (file.isDirectory) file.copyRecursively(dest, false) else file.copyTo(dest, false)
                if (file.isDirectory) file.deleteRecursively() else file.delete()
                true
            } catch (e: Exception) { false }
            activity.runOnUiThread {
                FileScanner.invalidate(); onChanged()
                toast(activity, if (ok) "Moved to secure folder" else "Failed")
            }
        }.start()
    }

    private fun checksumDialog(activity: Activity, file: File) {
        val working = AlertDialog.Builder(activity)
            .setMessage("Computing checksums…").setCancelable(false).create()
        working.show()
        Thread {
            val res = try { computeHashes(file) } catch (e: Exception) { null }
            activity.runOnUiThread {
                working.dismiss()
                if (res == null) { toast(activity, "Checksum failed"); return@runOnUiThread }
                val (md5, sha1, sha256) = res
                val msg = "MD5\n$md5\n\nSHA-1\n$sha1\n\nSHA-256\n$sha256"
                AlertDialog.Builder(activity)
                    .setTitle("Checksums · ${file.name}")
                    .setMessage(msg)
                    .setPositiveButton("Copy SHA-256") { _, _ ->
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("sha256", sha256))
                        toast(activity, "SHA-256 copied")
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }.start()
    }

    private fun computeHashes(file: File): Triple<String, String, String> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val r = ins.read(buf); if (r < 0) break
                md5.update(buf, 0, r); sha1.update(buf, 0, r); sha256.update(buf, 0, r)
            }
        }
        return Triple(hex(md5.digest()), hex(sha1.digest()), hex(sha256.digest()))
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xFF))
        return sb.toString()
    }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    /** Run a (possibly slow) elevated shell op off the UI thread, deliver result on it. */
    private fun bg(activity: Activity, work: () -> Boolean, done: (Boolean) -> Unit) {
        Thread {
            val ok = try { work() } catch (e: Exception) { false }
            activity.runOnUiThread { done(ok) }
        }.start()
    }
}
