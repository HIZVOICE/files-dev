package com.hyperfiles.manager

import android.content.Context
import java.io.File

/**
 * Simple recycle bin. Files are moved into .FilesDevTrash on the primary volume
 * (fast same-volume rename) and an index in SharedPrefs records the original path
 * and deletion time so they can be restored.
 */
object TrashBin {

    private const val SEP = ""

    data class Entry(val file: File, val originalPath: String, val deletedAt: Long) {
        val originalName: String get() = File(originalPath).name
        val originalParent: String get() = File(originalPath).parent ?: ""
    }

    fun dir(): File = File(StorageUtil.primaryStorage(), ".FilesDevTrash").apply { mkdirs() }

    private fun index(c: Context) = c.getSharedPreferences("trash_index", Context.MODE_PRIVATE)

    fun trash(c: Context, file: File): Boolean {
        if (!file.exists()) return false
        val target = unique(dir(), file.name)
        val moved = try {
            if (file.renameTo(target)) true
            else {
                if (file.isDirectory) {
                    if (file.copyRecursively(target, false)) { file.deleteRecursively(); true } else false
                } else {
                    file.copyTo(target, false); file.delete(); true
                }
            }
        } catch (e: Exception) { false }
        if (moved) index(c).edit().putString(target.name, "${file.absolutePath}$SEP${System.currentTimeMillis()}").apply()
        return moved
    }

    fun list(c: Context): List<Entry> {
        val idx = index(c)
        return (dir().listFiles() ?: emptyArray()).map { f ->
            val raw = idx.getString(f.name, null)
            val parts = raw?.split(SEP)
            val orig = parts?.getOrNull(0) ?: f.name
            val time = parts?.getOrNull(1)?.toLongOrNull() ?: f.lastModified()
            Entry(f, orig, time)
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(c: Context, entry: Entry): Boolean {
        var dest = File(entry.originalPath)
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest = unique(dest.parentFile ?: dir(), dest.name)
        val ok = try {
            if (entry.file.renameTo(dest)) true
            else {
                if (entry.file.isDirectory) {
                    if (entry.file.copyRecursively(dest, false)) { entry.file.deleteRecursively(); true } else false
                } else {
                    entry.file.copyTo(dest, false); entry.file.delete(); true
                }
            }
        } catch (e: Exception) { false }
        if (ok) { index(c).edit().remove(entry.file.name).apply(); FileScanner.invalidate() }
        return ok
    }

    fun deleteForever(c: Context, entry: Entry): Boolean {
        val ok = if (entry.file.isDirectory) entry.file.deleteRecursively() else entry.file.delete()
        if (ok) index(c).edit().remove(entry.file.name).apply()
        return ok
    }

    fun empty(c: Context) {
        (dir().listFiles() ?: emptyArray()).forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        index(c).edit().clear().apply()
    }

    fun count(c: Context): Int = dir().listFiles()?.size ?: 0

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }
}
