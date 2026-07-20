package com.hyperfiles.manager

import android.content.Context
import com.hyperfiles.manager.data.Db
import com.hyperfiles.manager.data.TrashEntry
import java.io.File

/**
 * Recycle bin. Files are moved into .FilesDevTrash on the primary volume (fast
 * same-volume rename) and a Room-backed index records the original path and
 * deletion time so they can be restored.
 */
object TrashBin {

    data class Entry(val file: File, val originalPath: String, val deletedAt: Long) {
        val originalName: String get() = File(originalPath).name
        val originalParent: String get() = File(originalPath).parent ?: ""
    }

    fun dir(): File = File(StorageUtil.primaryStorage(), ".FilesDevTrash").apply { mkdirs() }

    private fun dao(c: Context) = Db.get(c).trashDao()

    fun trash(c: Context, file: File): Boolean {
        if (!file.exists()) return false
        val target = unique(dir(), file.name)
        val moved = try {
            if (file.renameTo(target)) true
            else if (file.isDirectory) {
                if (file.copyRecursively(target, false)) { file.deleteRecursively(); true } else false
            } else {
                file.copyTo(target, false); file.delete(); true
            }
        } catch (e: Exception) { false }
        if (moved) dao(c).insert(TrashEntry(target.name, file.absolutePath, System.currentTimeMillis()))
        return moved
    }

    fun list(c: Context): List<Entry> {
        val byName = dao(c).all().associateBy { it.trashName }
        return (dir().listFiles() ?: emptyArray()).map { f ->
            val e = byName[f.name]
            Entry(f, e?.originalPath ?: f.name, e?.deletedAt ?: f.lastModified())
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(c: Context, entry: Entry): Boolean {
        var dest = File(entry.originalPath)
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest = unique(dest.parentFile ?: dir(), dest.name)
        val ok = try {
            if (entry.file.renameTo(dest)) true
            else if (entry.file.isDirectory) {
                if (entry.file.copyRecursively(dest, false)) { entry.file.deleteRecursively(); true } else false
            } else {
                entry.file.copyTo(dest, false); entry.file.delete(); true
            }
        } catch (e: Exception) { false }
        if (ok) { dao(c).delete(entry.file.name); FileScanner.invalidate() }
        return ok
    }

    fun deleteForever(c: Context, entry: Entry): Boolean {
        val ok = if (entry.file.isDirectory) entry.file.deleteRecursively() else entry.file.delete()
        if (ok) dao(c).delete(entry.file.name)
        return ok
    }

    fun empty(c: Context) {
        (dir().listFiles() ?: emptyArray()).forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        dao(c).clear()
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
