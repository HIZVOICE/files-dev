package com.hyperfiles.manager

import java.io.File

/** Builds a playlist from media siblings in the same folder. */
object Playlist {

    fun siblings(file: File, audio: Boolean): Pair<List<File>, Int> {
        val dir = file.parentFile ?: return Pair(listOf(file), 0)
        val all = dir.listFiles()
            ?.filter { !it.isDirectory && if (audio) FileTypes.isAudio(it.name) else FileTypes.isVideo(it.name) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        if (all.isEmpty()) return Pair(listOf(file), 0)
        val idx = all.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
        return Pair(all, idx)
    }

    /** Sibling images in the same folder, for swipe-through in the image viewer. */
    fun imageSiblings(file: File): Pair<List<File>, Int> {
        val dir = file.parentFile ?: return Pair(listOf(file), 0)
        val all = dir.listFiles()
            ?.filter { !it.isDirectory && FileTypes.isImage(it.name) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        if (all.isEmpty()) return Pair(listOf(file), 0)
        val idx = all.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
        return Pair(all, idx)
    }
}
