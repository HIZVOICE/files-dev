package com.hyperfiles.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Recursively walks a storage root once and buckets files into categories.
 * Result is cached for the session to keep category screens responsive.
 */
object FileScanner {

    @Volatile
    private var cache: Map<FileCategory, List<File>>? = null

    fun cached(): Map<FileCategory, List<File>>? = cache

    fun invalidate() {
        cache = null
    }

    suspend fun scan(root: File, force: Boolean = false): Map<FileCategory, List<File>> =
        withContext(Dispatchers.IO) {
            cache?.let { if (!force) return@withContext it }
            val map = LinkedHashMap<FileCategory, MutableList<File>>()
            for (c in FileCategory.values()) map[c] = mutableListOf()

            val stack = ArrayDeque<File>()
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    try {
                        if (f.isDirectory) {
                            val n = f.name
                            if (n == ".thumbnails" || n == ".trash" || n == ".FilesDevTrash" || n.equals("cache", true)) continue
                            stack.addLast(f)
                        } else {
                            val cat = FileTypes.categoryOf(f)
                            if (cat != FileCategory.OTHER) map[cat]?.add(f)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            val result: Map<FileCategory, List<File>> = map
            cache = result
            result
        }

    /** Most recently modified media/documents across all buckets. */
    fun recents(map: Map<FileCategory, List<File>>, limit: Int = 30): List<File> {
        val all = ArrayList<File>()
        for (c in listOf(FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO,
                FileCategory.DOCUMENTS, FileCategory.ARCHIVES, FileCategory.APKS)) {
            map[c]?.let { all.addAll(it) }
        }
        return all.sortedByDescending { it.lastModified() }.take(limit)
    }

    fun count(map: Map<FileCategory, List<File>>, c: FileCategory): Int = map[c]?.size ?: 0
}
