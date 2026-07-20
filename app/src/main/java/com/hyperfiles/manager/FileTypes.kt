package com.hyperfiles.manager

import java.io.File
import java.util.Locale

enum class FileCategory(val title: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("Apps"),
    DOWNLOADS("Downloads"),
    OTHER("Other")
}

object FileTypes {
    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff", "svg", "ico")
    val VIDEO = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2", "mpg", "mpeg",
        "ts", "m2ts", "mts", "vob", "ogv", "rmvb", "rm", "divx", "asf", "f4v")
    val AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "m4b", "wma", "opus", "amr", "mid",
        "midi", "aiff", "aif", "ape", "ac3", "dts", "mka", "wv")
    val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp",
        "csv", "md", "epub")
    val ARCHIVE = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "tbz", "txz",
        "tar.gz", "tar.bz2", "tar.xz", "lzma", "cpio", "jar", "br", "lz4", "sz",
        "z", "taz", "arj")
    val APK = setOf("apk", "apks", "xapk", "apkm", "aab")
    val TEXT = setOf("txt", "md", "log", "json", "xml", "html", "htm", "csv", "yml", "yaml", "ini", "conf",
        "cfg", "properties", "gradle", "java", "kt", "c", "cpp", "h", "py", "js", "ts", "css", "sh", "rtf")
    val BINARY = setOf("bin", "dat", "hex", "rom", "dmp", "dump", "iso", "img")

    fun ext(name: String): String {
        val lower = name.lowercase(Locale.US)
        for (d in listOf("tar.gz", "tar.bz2", "tar.xz")) if (lower.endsWith(".$d")) return d
        val i = lower.lastIndexOf('.')
        return if (i >= 0 && i < lower.length - 1) lower.substring(i + 1) else ""
    }

    fun categoryOf(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.OTHER
        return when (ext(file.name)) {
            in IMAGE -> FileCategory.IMAGES
            in VIDEO -> FileCategory.VIDEOS
            in AUDIO -> FileCategory.AUDIO
            in APK -> FileCategory.APKS
            in ARCHIVE -> FileCategory.ARCHIVES
            in DOC -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }

    val OFFICE = setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp")

    fun isImage(name: String) = ext(name) in IMAGE
    fun isVideo(name: String) = ext(name) in VIDEO
    fun isAudio(name: String) = ext(name) in AUDIO
    fun isMedia(name: String) = isVideo(name) || isAudio(name)
    fun isArchive(name: String) = ext(name) in ARCHIVE
    fun isApk(name: String) = ext(name) in APK
    fun isText(name: String) = ext(name) in TEXT
    fun isBinary(name: String) = ext(name) in BINARY
    fun isPdf(name: String) = ext(name) == "pdf"
    fun isWeb(name: String) = ext(name) in setOf("html", "htm")
    fun isOffice(name: String) = ext(name) in OFFICE
    fun isDocViewable(name: String) = isPdf(name) || isWeb(name) || isOffice(name)

    fun iconFor(file: File): Int {
        if (file.isDirectory) return R.drawable.ic_folder
        return when (categoryOf(file)) {
            FileCategory.IMAGES -> R.drawable.ic_image
            FileCategory.VIDEOS -> R.drawable.ic_video
            FileCategory.AUDIO -> R.drawable.ic_audio
            FileCategory.DOCUMENTS -> R.drawable.ic_doc
            FileCategory.ARCHIVES -> R.drawable.ic_archive
            FileCategory.APKS -> R.drawable.ic_apk
            else -> if (isBinary(file.name)) R.drawable.ic_bin else R.drawable.ic_file
        }
    }
}
