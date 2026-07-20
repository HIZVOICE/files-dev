package com.hyperfiles.manager

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Archive listing, extraction and 7z creation backed by Apache Commons Compress
 * (with org.tukaani:xz for LZMA/LZMA2). ZIP extraction reuses the stdlib ZipUtil.
 */
object ArchiveEngine {

    data class Entry(val name: String, val size: Long, val isDirectory: Boolean)

    enum class Format { SEVENZ, ZIP, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ, UNKNOWN }

    /** Extension first (handles the compound tar.* names); magic-byte sniff as a fallback. */
    fun detectFormat(file: File): Format {
        val byExt = detectByExtension(file.name)
        return if (byExt != Format.UNKNOWN) byExt else detectByMagic(file)
    }

    private fun detectByExtension(rawName: String): Format {
        val n = rawName.lowercase()
        return when {
            n.endsWith(".7z") -> Format.SEVENZ
            n.endsWith(".zip") || n.endsWith(".jar") -> Format.ZIP
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Format.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> Format.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Format.TAR_XZ
            n.endsWith(".tar") -> Format.TAR
            n.endsWith(".gz") -> Format.GZ
            n.endsWith(".bz2") -> Format.BZ2
            n.endsWith(".xz") -> Format.XZ
            else -> Format.UNKNOWN
        }
    }

    /** Sniff the header so an extensionless / misnamed archive still opens. */
    private fun detectByMagic(file: File): Format = try {
        val h = ByteArray(512)
        val read = FileInputStream(file).use { ins ->
            var t = 0
            while (t < h.size) { val r = ins.read(h, t, h.size - t); if (r < 0) break; t += r }
            t
        }
        fun at(off: Int, vararg b: Int): Boolean {
            if (read < off + b.size) return false
            for (i in b.indices) if ((h[off + i].toInt() and 0xFF) != b[i]) return false
            return true
        }
        when {
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Format.SEVENZ
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) ||
                at(0, 0x50, 0x4B, 0x07, 0x08) -> Format.ZIP
            at(0, 0x1F, 0x8B) -> Format.GZ
            at(0, 0x42, 0x5A, 0x68) -> Format.BZ2
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> Format.XZ
            at(257, 0x75, 0x73, 0x74, 0x61, 0x72) -> Format.TAR   // "ustar"
            else -> Format.UNKNOWN
        }
    } catch (e: Exception) { Format.UNKNOWN }

    fun isSupported(file: File) = detectFormat(file) != Format.UNKNOWN

    // ---- Output location helpers ---------------------------------------

    /** Strip archive extension(s) for a default output-folder name. */
    fun baseName(f: File): String {
        val n = f.name
        val lower = n.lowercase()
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz"))
            if (lower.endsWith(s)) return n.substring(0, n.length - s.length)
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else n
    }

    private fun canWriteInto(dir: File): Boolean = try {
        dir.isDirectory && dir.canWrite() && !SafHelper.isRestricted(dir)
    } catch (_: Exception) { false }

    private fun uniqueDir(parent: File, name: String): File {
        var f = File(parent, name); var i = 1
        while (f.exists()) { f = File(parent, "$name ($i)"); i++ }
        return f
    }

    /**
     * A writable directory to extract into: the archive's own folder when it's
     * writable, otherwise Download/FilesDev on the primary volume (always
     * writable with all-files access, and never a restricted Android/data path).
     */
    fun writableExtractDir(preferredParent: File, baseName: String): File {
        val parent = if (canWriteInto(preferredParent)) preferredParent
        else File(StorageUtil.primaryStorage(), "Download/FilesDev").apply { mkdirs() }
        return uniqueDir(parent, baseName)
    }

    private fun strippedName(file: File): String {
        val n = file.name
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else "$n.out"
    }

    private fun tarStream(file: File, format: Format): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        val inner: InputStream = when (format) {
            Format.TAR_GZ -> GzipCompressorInputStream(raw)
            Format.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            Format.TAR_XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(inner)
    }

    // ---- Listing --------------------------------------------------------

    @Suppress("DEPRECATION")
    fun list(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        when (detectFormat(file)) {
            Format.SEVENZ -> SevenZFile(file).use { sz ->
                var e = sz.nextEntry
                while (e != null) { out.add(Entry(e.name, e.size, e.isDirectory)); e = sz.nextEntry }
            }
            Format.ZIP -> ZipFile(file).use { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val z = en.nextElement()
                    out.add(Entry(z.name, if (z.size >= 0) z.size else 0, z.isDirectory))
                }
            }
            Format.TAR, Format.TAR_GZ, Format.TAR_BZ2, Format.TAR_XZ -> {
                val fmt = detectFormat(file)
                tarStream(file, fmt).use { tis ->
                    var e = tis.nextEntry
                    while (e != null) { out.add(Entry(e.name, e.size, e.isDirectory)); e = tis.nextEntry }
                }
            }
            Format.GZ, Format.BZ2, Format.XZ ->
                out.add(Entry(strippedName(file), -1, false))
            Format.UNKNOWN -> {}
        }
        return out
    }

    // ---- Extract all ----------------------------------------------------

    @Suppress("DEPRECATION")
    fun extractAll(file: File, destDir: File) {
        destDir.mkdirs()
        val canonical = destDir.canonicalPath + File.separator
        fun safe(name: String): File {
            val f = File(destDir, name)
            if (!f.canonicalPath.startsWith(canonical)) throw SecurityException("Entry escapes target: $name")
            return f
        }
        when (detectFormat(file)) {
            Format.SEVENZ -> SevenZFile(file).use { sz ->
                var e = sz.nextEntry
                val buf = ByteArray(8192)
                while (e != null) {
                    val outFile = safe(e.name)
                    if (e.isDirectory) outFile.mkdirs()
                    else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            while (true) { val r = sz.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                        }
                    }
                    e = sz.nextEntry
                }
            }
            Format.ZIP -> ZipUtil.unzip(file, destDir)
            Format.TAR, Format.TAR_GZ, Format.TAR_BZ2, Format.TAR_XZ -> {
                tarStream(file, detectFormat(file)).use { tis ->
                    var e = tis.nextEntry
                    val buf = ByteArray(8192)
                    while (e != null) {
                        val outFile = safe(e.name)
                        if (e.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                while (true) { val r = tis.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                            }
                        }
                        e = tis.nextEntry
                    }
                }
            }
            Format.GZ, Format.BZ2, Format.XZ -> {
                val outFile = safe(strippedName(file))
                singleStream(file).use { ins -> FileOutputStream(outFile).use { ins.copyTo(it) } }
            }
            Format.UNKNOWN -> throw IllegalArgumentException("Unsupported archive")
        }
    }

    private fun singleStream(file: File): InputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        return when (detectFormat(file)) {
            Format.GZ -> GzipCompressorInputStream(raw)
            Format.BZ2 -> BZip2CompressorInputStream(raw)
            Format.XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
    }

    // ---- Extract a single entry (for in-app preview) --------------------

    @Suppress("DEPRECATION")
    fun extractEntry(file: File, entryName: String, outFile: File): Boolean {
        outFile.parentFile?.mkdirs()
        when (detectFormat(file)) {
            Format.SEVENZ -> SevenZFile(file).use { sz ->
                var e = sz.nextEntry
                val buf = ByteArray(8192)
                while (e != null) {
                    if (e.name == entryName && !e.isDirectory) {
                        FileOutputStream(outFile).use { fos ->
                            while (true) { val r = sz.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                        }
                        return true
                    }
                    e = sz.nextEntry
                }
            }
            Format.ZIP -> ZipFile(file).use { zf ->
                val entry = zf.getEntry(entryName) ?: return false
                zf.getInputStream(entry).use { ins -> FileOutputStream(outFile).use { ins.copyTo(it) } }
                return true
            }
            Format.TAR, Format.TAR_GZ, Format.TAR_BZ2, Format.TAR_XZ -> {
                tarStream(file, detectFormat(file)).use { tis ->
                    var e = tis.nextEntry
                    val buf = ByteArray(8192)
                    while (e != null) {
                        if (e.name == entryName && !e.isDirectory) {
                            FileOutputStream(outFile).use { fos ->
                                while (true) { val r = tis.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                            }
                            return true
                        }
                        e = tis.nextEntry
                    }
                }
            }
            Format.GZ, Format.BZ2, Format.XZ -> {
                singleStream(file).use { ins -> FileOutputStream(outFile).use { ins.copyTo(it) } }
                return true
            }
            Format.UNKNOWN -> return false
        }
        return false
    }

    // ---- Create 7z ------------------------------------------------------

    fun create7z(files: List<File>, out: File) {
        SevenZOutputFile(out).use { szo ->
            for (f in files) add7z(szo, f, f.name)
            szo.finish()
        }
    }

    private fun add7z(szo: SevenZOutputFile, file: File, entryName: String) {
        if (file.isDirectory) {
            val entry = szo.createArchiveEntry(file, "$entryName/")
            szo.putArchiveEntry(entry)
            szo.closeArchiveEntry()
            file.listFiles()?.forEach { add7z(szo, it, "$entryName/${it.name}") }
        } else {
            val entry = szo.createArchiveEntry(file, entryName)
            szo.putArchiveEntry(entry)
            val buf = ByteArray(8192)
            FileInputStream(file).use { ins ->
                while (true) { val r = ins.read(buf); if (r < 0) break; szo.write(buf, 0, r) }
            }
            szo.closeArchiveEntry()
        }
    }
}
