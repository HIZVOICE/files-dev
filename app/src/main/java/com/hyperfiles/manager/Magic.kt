package com.hyperfiles.manager

/** Content-based file type detection from a file's header bytes. */
object Magic {

    enum class Kind { IMAGE, VIDEO, AUDIO, ARCHIVE, PDF, EXECUTABLE, SYSTEM, TEXT, UNKNOWN }
    data class Detected(val label: String, val kind: Kind)

    fun detect(h: ByteArray): Detected {
        fun at(off: Int, vararg b: Int): Boolean {
            if (h.size < off + b.size) return false
            for (i in b.indices) if ((h[off + i].toInt() and 0xFF) != b[i]) return false
            return true
        }
        fun ascii(off: Int, s: String): Boolean {
            if (h.size < off + s.length) return false
            for (i in s.indices) if ((h[off + i].toInt() and 0xFF) != s[i].code) return false
            return true
        }

        return when {
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Detected("7-Zip archive", Kind.ARCHIVE)
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> Detected("ZIP archive", Kind.ARCHIVE)
            ascii(0, "Rar!") -> Detected("RAR archive", Kind.ARCHIVE)
            at(0, 0x1F, 0x8B) -> Detected("GZIP archive", Kind.ARCHIVE)
            at(0, 0x42, 0x5A, 0x68) -> Detected("BZIP2 archive", Kind.ARCHIVE)
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> Detected("XZ archive", Kind.ARCHIVE)
            ascii(257, "ustar") -> Detected("TAR archive", Kind.ARCHIVE)
            at(0, 0x89, 0x50, 0x4E, 0x47) -> Detected("PNG image", Kind.IMAGE)
            at(0, 0xFF, 0xD8, 0xFF) -> Detected("JPEG image", Kind.IMAGE)
            at(0, 0x47, 0x49, 0x46, 0x38) -> Detected("GIF image", Kind.IMAGE)
            at(0, 0x42, 0x4D) -> Detected("BMP image", Kind.IMAGE)
            ascii(0, "RIFF") && ascii(8, "WEBP") -> Detected("WebP image", Kind.IMAGE)
            at(0, 0x25, 0x50, 0x44, 0x46) -> Detected("PDF document", Kind.PDF)
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> Detected("Matroska / WebM video", Kind.VIDEO)
            ascii(4, "ftyp") -> Detected("MP4 / MOV video", Kind.VIDEO)
            ascii(0, "RIFF") && ascii(8, "AVI ") -> Detected("AVI video", Kind.VIDEO)
            ascii(0, "RIFF") && ascii(8, "WAVE") -> Detected("WAV audio", Kind.AUDIO)
            at(0, 0x49, 0x44, 0x33) || at(0, 0xFF, 0xFB) || at(0, 0xFF, 0xF3) -> Detected("MP3 audio", Kind.AUDIO)
            ascii(0, "OggS") -> Detected("OGG media", Kind.AUDIO)
            ascii(0, "fLaC") -> Detected("FLAC audio", Kind.AUDIO)
            ascii(0, "ANDROID!") -> Detected("Android boot image", Kind.SYSTEM)
            ascii(0, "VNDRBOOT") -> Detected("Android vendor boot image", Kind.SYSTEM)
            at(0, 0x3A, 0xFF, 0x26, 0xED) -> Detected("Android sparse image", Kind.SYSTEM)
            at(0, 0xD0, 0x0D, 0xFE, 0xED) -> Detected("Device tree blob (DTB/DTBO)", Kind.SYSTEM)
            ascii(0, "LOGO!!!!") -> Detected("MTK logo image", Kind.SYSTEM)
            at(0x438, 0x53, 0xEF) -> Detected("ext2/3/4 filesystem image", Kind.SYSTEM)
            at(0, 0x7F, 0x45, 0x4C, 0x46) -> Detected("ELF executable / library", Kind.EXECUTABLE)
            at(0, 0x4D, 0x5A) -> Detected("Windows executable (PE)", Kind.EXECUTABLE)
            at(0, 0x02, 0x21, 0x4C, 0x18) -> Detected("LZ4 compressed", Kind.ARCHIVE)
            at(0, 0x28, 0xB5, 0x2F, 0xFD) -> Detected("Zstandard compressed", Kind.ARCHIVE)
            looksTextual(h) -> Detected("Text data", Kind.TEXT)
            else -> Detected("Binary data", Kind.UNKNOWN)
        }
    }

    private fun looksTextual(h: ByteArray): Boolean {
        if (h.isEmpty()) return false
        var printable = 0
        val n = minOf(h.size, 512)
        for (i in 0 until n) {
            val c = h[i].toInt() and 0xFF
            if (c == 9 || c == 10 || c == 13 || c in 32..126) printable++
        }
        return printable.toDouble() / n > 0.90
    }

    fun hexDump(bytes: ByteArray, max: Int): String {
        val sb = StringBuilder()
        val n = minOf(bytes.size, max)
        var i = 0
        while (i < n) {
            sb.append(String.format("%08X  ", i))
            val end = minOf(i + 16, n)
            for (j in i until i + 16) {
                if (j < end) sb.append(String.format("%02X ", bytes[j].toInt() and 0xFF)) else sb.append("   ")
                if (j - i == 7) sb.append(" ")
            }
            sb.append(" |")
            for (j in i until end) {
                val c = bytes[j].toInt() and 0xFF
                sb.append(if (c in 32..126) c.toChar() else '.')
            }
            sb.append("|\n")
            i += 16
        }
        return sb.toString()
    }
}
