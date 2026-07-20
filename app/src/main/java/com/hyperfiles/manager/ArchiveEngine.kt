package com.hyperfiles.manager

import com.github.junrar.Archive as RarArchive
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 7-Zip-class archive listing / extraction / creation.
 *
 *  - ZIP  → Apache Commons Compress [ApacheZipFile] (random access, full ZIP64 —
 *           unlike java.util.zip it reads huge / stored OTA zips without the
 *           "END header not found" failure).
 *  - 7z   → [SevenZFile]
 *  - RAR  → junrar (RAR4 + RAR5, read-only)
 *  - tar (+ gz/bz2/xz/lzma/Z/lz4/zstd), cpio, ar, arj, dump, and standalone
 *    compressors (gz, bz2, xz, lzma, Z, lz4, snappy, zstd, brotli) → Commons
 *    Compress auto-detection (CompressorStreamFactory + ArchiveStreamFactory).
 *
 * Callers pass a directly-readable File (see Readable.resolve for Android/data
 * and root-only paths). Exceptions propagate so the UI can show the real cause.
 */
object ArchiveEngine {

    data class Entry(val name: String, val size: Long, val isDirectory: Boolean)

    enum class Format {
        ZIP, SEVENZ, RAR, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ, LZMA, Z,
        LZ4, SNAPPY, ZSTD, BROTLI, CPIO, AR, ARJ, DUMP, UNKNOWN
    }

    // ---- Format label (subtitle) + support gate ------------------------

    fun detectFormat(file: File): Format {
        val byExt = detectByExtension(file.name)
        return if (byExt != Format.UNKNOWN) byExt else detectByMagic(file)
    }

    private fun detectByExtension(rawName: String): Format {
        val n = rawName.lowercase()
        return when {
            n.endsWith(".7z") -> Format.SEVENZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".apks") ||
                n.endsWith(".xapk") || n.endsWith(".apkm") || n.endsWith(".aab") -> Format.ZIP
            n.endsWith(".rar") -> Format.RAR
            n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".taz") -> Format.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> Format.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Format.TAR_XZ
            n.endsWith(".tar") -> Format.TAR
            n.endsWith(".gz") -> Format.GZ
            n.endsWith(".bz2") -> Format.BZ2
            n.endsWith(".xz") -> Format.XZ
            n.endsWith(".lzma") -> Format.LZMA
            n.endsWith(".z") -> Format.Z
            n.endsWith(".lz4") -> Format.LZ4
            n.endsWith(".sz") -> Format.SNAPPY
            n.endsWith(".br") -> Format.BROTLI
            n.endsWith(".cpio") -> Format.CPIO
            n.endsWith(".arj") -> Format.ARJ
            else -> Format.UNKNOWN
        }
    }

    private fun detectByMagic(file: File): Format = try {
        val h = magic(file, 512)
        fun at(off: Int, vararg b: Int): Boolean {
            if (h.size < off + b.size) return false
            for (i in b.indices) if ((h[off + i].toInt() and 0xFF) != b[i]) return false
            return true
        }
        when {
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Format.SEVENZ
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) ||
                at(0, 0x50, 0x4B, 0x07, 0x08) -> Format.ZIP
            at(0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> Format.RAR
            at(0, 0x1F, 0x8B) -> Format.GZ
            at(0, 0x42, 0x5A, 0x68) -> Format.BZ2
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> Format.XZ
            at(0, 0x04, 0x22, 0x4D, 0x18) -> Format.LZ4
            at(0, 0x1F, 0x9D) -> Format.Z
            at(257, 0x75, 0x73, 0x74, 0x61, 0x72) -> Format.TAR   // "ustar"
            else -> Format.UNKNOWN
        }
    } catch (e: Exception) { Format.UNKNOWN }

    fun isSupported(file: File) = detectFormat(file) != Format.UNKNOWN

    private fun magic(file: File, len: Int): ByteArray = try {
        val h = ByteArray(len)
        var t = 0
        FileInputStream(file).use { while (t < len) { val r = it.read(h, t, len - t); if (r < 0) break; t += r } }
        if (t == len) h else h.copyOf(t)
    } catch (e: Exception) { ByteArray(0) }

    // ---- Dispatch ------------------------------------------------------

    private fun isSevenZ(file: File): Boolean =
        file.name.lowercase().endsWith(".7z") || detectByMagic(file) == Format.SEVENZ

    private fun isRar(file: File): Boolean =
        file.name.lowercase().endsWith(".rar") || detectByMagic(file) == Format.RAR

    private fun isZipContainer(file: File): Boolean {
        val n = file.name.lowercase()
        if (n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".apks") ||
            n.endsWith(".xapk") || n.endsWith(".apkm") || n.endsWith(".aab")) return true
        val m = magic(file, 4)
        return m.size >= 2 && m[0] == 0x50.toByte() && m[1] == 0x4B.toByte()
    }

    fun list(file: File): List<Entry> = when {
        isSevenZ(file) -> sevenZList(file)
        isRar(file) -> rarList(file)
        isZipContainer(file) -> zipList(file)
        else -> streamList(file)
    }

    /**
     * Extract everything to [destDir]. [onProgress] reports overall percent 0..100,
     * or -1 for an indeterminate phase (formats whose total size isn't known upfront).
     */
    fun extractAll(file: File, destDir: File, onProgress: (Int) -> Unit = {}) {
        destDir.mkdirs()
        when {
            isSevenZ(file) -> sevenZExtract(file, destDir, onProgress)
            isRar(file) -> rarExtract(file, destDir, onProgress)
            isZipContainer(file) -> zipExtract(file, destDir, onProgress)
            else -> streamExtract(file, destDir, onProgress)
        }
        onProgress(100)
    }

    /** Accumulates written bytes and reports whole-percent changes. */
    private class Sink(private val total: Long, private val cb: (Int) -> Unit) {
        private var done = 0L
        private var last = -1
        fun add(n: Int) {
            if (total <= 0 || n <= 0) return
            done += n
            val p = ((done * 100) / total).toInt().coerceIn(0, 100)
            if (p != last) { last = p; cb(p) }
        }
    }

    /** OutputStream that reports bytes to a Sink (for libraries that write internally, e.g. junrar). */
    private class CountingOut(private val out: OutputStream, private val sink: Sink) : OutputStream() {
        override fun write(b: Int) { out.write(b); sink.add(1) }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); sink.add(len) }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    fun extractEntry(file: File, entryName: String, outFile: File): Boolean {
        outFile.parentFile?.mkdirs()
        return when {
            isSevenZ(file) -> sevenZEntry(file, entryName, outFile)
            isRar(file) -> rarEntry(file, entryName, outFile)
            isZipContainer(file) -> zipEntry(file, entryName, outFile)
            else -> streamEntry(file, entryName, outFile)
        }
    }

    // ---- Shared helpers ------------------------------------------------

    private fun safeChild(destDir: File, name: String): File {
        val canonical = destDir.canonicalPath + File.separator
        val f = File(destDir, name)
        if (!f.canonicalPath.startsWith(canonical)) throw SecurityException("Entry escapes target: $name")
        return f
    }

    private fun copy(ins: InputStream, outFile: File) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { ins.copyTo(it) }
    }

    private fun strippedName(file: File): String {
        val n = file.name
        val i = n.lastIndexOf('.')
        return if (i > 0) n.substring(0, i) else "$n.out"
    }

    // ---- ZIP (Apache Commons Compress — robust / ZIP64) ----------------

    @Suppress("DEPRECATION")
    private fun zipList(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        ApacheZipFile(file).use { zf ->
            val en = zf.entries
            while (en.hasMoreElements()) {
                val z = en.nextElement()
                out.add(Entry(z.name, if (z.size >= 0) z.size else 0, z.isDirectory))
            }
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun zipExtract(file: File, destDir: File, onProgress: (Int) -> Unit) {
        ApacheZipFile(file).use { zf ->
            var total = 0L
            run {
                val en = zf.entries
                while (en.hasMoreElements()) { val z = en.nextElement(); if (!z.isDirectory && z.size > 0) total += z.size }
            }
            if (total <= 0) onProgress(-1)
            val sink = Sink(total, onProgress)
            val buf = ByteArray(1 shl 16)
            val en = zf.entries
            while (en.hasMoreElements()) {
                val z = en.nextElement()
                val outFile = safeChild(destDir, z.name)
                if (z.isDirectory) outFile.mkdirs()
                else if (zf.canReadEntryData(z)) {
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(z).use { ins ->
                        FileOutputStream(outFile).use { fos ->
                            while (true) { val r = ins.read(buf); if (r < 0) break; fos.write(buf, 0, r); sink.add(r) }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun zipEntry(file: File, name: String, outFile: File): Boolean {
        ApacheZipFile(file).use { zf ->
            val e = zf.getEntry(name) ?: return false
            zf.getInputStream(e).use { copy(it, outFile) }
            return true
        }
    }

    // ---- 7z ------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun sevenZList(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        SevenZFile(file).use { sz ->
            var e = sz.nextEntry
            while (e != null) { out.add(Entry(e.name, e.size, e.isDirectory)); e = sz.nextEntry }
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun sevenZTotal(file: File): Long {
        var t = 0L
        SevenZFile(file).use { sz -> for (e in sz.entries) if (!e.isDirectory && e.size > 0) t += e.size }
        return t
    }

    @Suppress("DEPRECATION")
    private fun sevenZExtract(file: File, destDir: File, onProgress: (Int) -> Unit) {
        val total = runCatching { sevenZTotal(file) }.getOrDefault(0L)
        if (total <= 0) onProgress(-1)
        val sink = Sink(total, onProgress)
        SevenZFile(file).use { sz ->
            var e = sz.nextEntry
            val buf = ByteArray(1 shl 16)
            while (e != null) {
                val outFile = safeChild(destDir, e.name)
                if (e.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) { val r = sz.read(buf); if (r < 0) break; fos.write(buf, 0, r); sink.add(r) }
                    }
                }
                e = sz.nextEntry
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sevenZEntry(file: File, name: String, outFile: File): Boolean {
        SevenZFile(file).use { sz ->
            var e = sz.nextEntry
            val buf = ByteArray(1 shl 16)
            while (e != null) {
                if (e.name == name && !e.isDirectory) {
                    FileOutputStream(outFile).use { fos ->
                        while (true) { val r = sz.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                    }
                    return true
                }
                e = sz.nextEntry
            }
        }
        return false
    }

    // ---- RAR (junrar) --------------------------------------------------

    private fun rarName(h: com.github.junrar.rarfile.FileHeader): String = h.fileName.replace('\\', '/')

    private fun rarList(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        RarArchive(file).use { a -> a.fileHeaders.forEach { out.add(Entry(rarName(it), it.fullUnpackSize, it.isDirectory)) } }
        return out
    }

    private fun rarExtract(file: File, destDir: File, onProgress: (Int) -> Unit) {
        RarArchive(file).use { a ->
            var total = 0L
            for (h in a.fileHeaders) if (!h.isDirectory) total += h.fullUnpackSize
            if (total <= 0) onProgress(-1)
            val sink = Sink(total, onProgress)
            for (h in a.fileHeaders) {
                val outFile = safeChild(destDir, rarName(h))
                if (h.isDirectory) outFile.mkdirs()
                else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { a.extractFile(h, CountingOut(it, sink)) } }
            }
        }
    }

    private fun rarEntry(file: File, name: String, outFile: File): Boolean {
        RarArchive(file).use { a ->
            val h = a.fileHeaders.firstOrNull { rarName(it) == name } ?: return false
            FileOutputStream(outFile).use { a.extractFile(h, it) }
            return true
        }
    }

    // ---- Everything else via Commons Compress auto-detection -----------

    /** A decompressed stream if the file is a recognized compressor, else null. */
    private fun compressorStream(file: File): InputStream? {
        val n = file.name.lowercase()
        return when {
            n.endsWith(".br") -> runCatching { BrotliCompressorInputStream(BufferedInputStream(FileInputStream(file))) }.getOrNull()
            n.endsWith(".lzma") -> runCatching { LZMACompressorInputStream(BufferedInputStream(FileInputStream(file))) }.getOrNull()
            else -> runCatching {
                CompressorStreamFactory().createCompressorInputStream(BufferedInputStream(FileInputStream(file)))
            }.getOrNull()
        }
    }

    /** Concrete stream for a detected archiver name (avoids the factory's generic inference). */
    private fun archiverFrom(name: String, input: InputStream): ArchiveInputStream<*>? = when (name) {
        ArchiveStreamFactory.TAR -> TarArchiveInputStream(input)
        ArchiveStreamFactory.CPIO -> CpioArchiveInputStream(input)
        ArchiveStreamFactory.AR -> ArArchiveInputStream(input)
        ArchiveStreamFactory.ARJ -> ArjArchiveInputStream(input)
        ArchiveStreamFactory.DUMP -> DumpArchiveInputStream(input)
        ArchiveStreamFactory.ZIP, ArchiveStreamFactory.JAR -> ZipArchiveInputStream(input)
        else -> null
    }

    /** An ArchiveInputStream (decompressing first if needed) or null if the file isn't a stream archive. */
    private fun openArchive(file: File): ArchiveInputStream<*>? {
        val comp = compressorStream(file)
        val input = BufferedInputStream(comp ?: FileInputStream(file))
        val name = runCatching { ArchiveStreamFactory.detect(input) }.getOrNull()
        if (name == null) { runCatching { input.close() }; return null }
        val ais = runCatching { archiverFrom(name, input) }.getOrNull()
        if (ais == null) runCatching { input.close() }
        return ais
    }

    private fun streamList(file: File): List<Entry> {
        openArchive(file)?.use { ais ->
            val out = ArrayList<Entry>()
            var e = ais.nextEntry
            while (e != null) { out.add(Entry(e.name, if (e.size >= 0) e.size else 0, e.isDirectory)); e = ais.nextEntry }
            return out
        }
        // Not an archive — a standalone compressed file shows as a single entry.
        val c = compressorStream(file)
        return if (c != null) { runCatching { c.close() }; listOf(Entry(strippedName(file), -1, false)) } else emptyList()
    }

    private fun streamExtract(file: File, destDir: File, onProgress: (Int) -> Unit) {
        onProgress(-1)   // total isn't known upfront for streamed archives → indeterminate
        val ais = openArchive(file)
        if (ais != null) {
            ais.use {
                var e = it.nextEntry
                val buf = ByteArray(1 shl 16)
                while (e != null) {
                    if (it.canReadEntryData(e)) {
                        val outFile = safeChild(destDir, e.name)
                        if (e.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                while (true) { val r = it.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                            }
                        }
                    }
                    e = it.nextEntry
                }
            }
            return
        }
        val comp = compressorStream(file) ?: throw IllegalArgumentException("Unsupported or unreadable archive")
        comp.use { copy(it, safeChild(destDir, strippedName(file))) }
    }

    private fun streamEntry(file: File, name: String, outFile: File): Boolean {
        val ais = openArchive(file)
        if (ais != null) {
            ais.use {
                var e = it.nextEntry
                val buf = ByteArray(1 shl 16)
                while (e != null) {
                    if (e.name == name && !e.isDirectory) {
                        FileOutputStream(outFile).use { fos ->
                            while (true) { val r = it.read(buf); if (r < 0) break; fos.write(buf, 0, r) }
                        }
                        return true
                    }
                    e = it.nextEntry
                }
            }
            return false
        }
        val comp = compressorStream(file) ?: return false
        comp.use { copy(it, outFile) }
        return true
    }

    // ---- Create 7z -----------------------------------------------------

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
            val buf = ByteArray(1 shl 16)
            FileInputStream(file).use { ins ->
                while (true) { val r = ins.read(buf); if (r < 0) break; szo.write(buf, 0, r) }
            }
            szo.closeArchiveEntry()
        }
    }

    // ---- Output location helpers ---------------------------------------

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

    fun writableExtractDir(preferredParent: File, baseName: String): File {
        val parent = if (canWriteInto(preferredParent)) preferredParent
        else File(StorageUtil.primaryStorage(), "Download/FilesDev").apply { mkdirs() }
        return uniqueDir(parent, baseName)
    }
}
