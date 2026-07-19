package com.hyperfiles.manager

import java.io.File

object Sorting {
    enum class By { NAME, DATE, SIZE, TYPE }

    fun sort(list: MutableList<File>, by: By, asc: Boolean, foldersFirst: Boolean = true) {
        val cmp = Comparator<File> { a, b ->
            if (foldersFirst && a.isDirectory != b.isDirectory)
                return@Comparator if (a.isDirectory) -1 else 1
            val r = when (by) {
                By.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                By.DATE -> a.lastModified().compareTo(b.lastModified())
                By.SIZE -> a.length().compareTo(b.length())
                By.TYPE -> FileTypes.ext(a.name).compareTo(FileTypes.ext(b.name))
            }
            if (asc) r else -r
        }
        list.sortWith(cmp)
    }
}
