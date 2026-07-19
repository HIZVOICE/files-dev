package com.hyperfiles.manager

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Extracts partition images from an A/B OTA payload.bin (chromeos_update_engine
 * "CrAU" format). Supports FULL-OTA operations (REPLACE, REPLACE_XZ, REPLACE_BZ,
 * ZERO) which is what custom-ROM full packages use. Incremental ops
 * (SOURCE_*, *DIFF) need a base image and are reported as unsupported.
 */
object PayloadDumper {

    // InstallOperation.Type
    private const val REPLACE = 0
    private const val REPLACE_BZ = 1
    private const val REPLACE_XZ = 8
    private const val ZERO = 6

    data class Extent(val startBlock: Long, val numBlocks: Long)
    data class Operation(val type: Int, val dataOffset: Long, val dataLength: Long, val dst: List<Extent>)
    data class Partition(val name: String, val size: Long, val ops: List<Operation>) {
        val supported: Boolean get() = ops.all { it.type == REPLACE || it.type == REPLACE_BZ || it.type == REPLACE_XZ || it.type == ZERO }
    }
    data class Info(val version: Long, val blockSize: Long, val dataOffset: Long, val partitions: List<Partition>)

    fun isPayload(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { val m = ByteArray(4); it.readFully(m); String(m) == "CrAU" }
    } catch (e: Exception) { false }

    fun parse(file: File): Info {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4); raf.readFully(magic)
            require(String(magic) == "CrAU") { "Not a payload.bin (bad magic)" }
            val version = raf.readLong()
            val manifestSize = raf.readLong()
            val metaSigSize = if (version >= 2) raf.readInt() else 0
            val headerSize = 4 + 8 + 8 + (if (version >= 2) 4 else 0)
            val manifest = ByteArray(manifestSize.toInt())
            raf.seek(headerSize.toLong())
            raf.readFully(manifest)
            val dataOffset = headerSize + manifestSize + metaSigSize
            val (blockSize, partitions) = parseManifest(manifest)
            return Info(version, blockSize, dataOffset, partitions)
        }
    }

    fun extract(payload: File, info: Info, p: Partition, outFile: File, onProgress: (Int) -> Unit) {
        require(p.supported) { "Partition '${p.name}' uses incremental ops (needs a base image)" }
        outFile.parentFile?.mkdirs()
        RandomAccessFile(payload, "r").use { raf ->
            RandomAccessFile(outFile, "rw").use { out ->
                out.setLength(0)
                for ((i, op) in p.ops.withIndex()) {
                    if (op.type == ZERO) {
                        val zeros = ByteArray(64 * 1024)
                        for (ext in op.dst) {
                            out.seek(ext.startBlock * info.blockSize)
                            var remaining = ext.numBlocks * info.blockSize
                            while (remaining > 0) {
                                val n = minOf(remaining, zeros.size.toLong()).toInt()
                                out.write(zeros, 0, n); remaining -= n
                            }
                        }
                    } else {
                        raf.seek(info.dataOffset + op.dataOffset)
                        val blob = ByteArray(op.dataLength.toInt())
                        raf.readFully(blob)
                        val data = when (op.type) {
                            REPLACE -> blob
                            REPLACE_XZ -> XZCompressorInputStream(ByteArrayInputStream(blob)).use { it.readBytes() }
                            REPLACE_BZ -> BZip2CompressorInputStream(ByteArrayInputStream(blob)).use { it.readBytes() }
                            else -> throw UnsupportedOperationException("op ${op.type}")
                        }
                        var offset = 0
                        for (ext in op.dst) {
                            out.seek(ext.startBlock * info.blockSize)
                            val len = minOf((ext.numBlocks * info.blockSize).toInt(), data.size - offset)
                            if (len > 0) { out.write(data, offset, len); offset += len }
                        }
                    }
                    onProgress((i + 1) * 100 / p.ops.size)
                }
            }
        }
    }

    // ---- minimal protobuf ----
    private class Cursor(val b: ByteArray, var pos: Int, val end: Int)

    private fun varint(c: Cursor): Long {
        var result = 0L; var shift = 0
        while (c.pos < c.end) {
            val x = c.b[c.pos++].toInt() and 0xFF
            result = result or ((x and 0x7F).toLong() shl shift)
            if (x and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    private fun skip(c: Cursor, wire: Int) {
        when (wire) {
            0 -> varint(c)
            1 -> c.pos += 8
            2 -> { val len = varint(c).toInt(); c.pos += len }
            5 -> c.pos += 4
            else -> c.pos = c.end
        }
    }

    private fun parseManifest(bytes: ByteArray): Pair<Long, List<Partition>> {
        val c = Cursor(bytes, 0, bytes.size)
        var blockSize = 4096L
        val partitions = ArrayList<Partition>()
        while (c.pos < c.end) {
            val tag = varint(c); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 3 && wire == 0 -> blockSize = varint(c)
                field == 13 && wire == 2 -> {
                    val len = varint(c).toInt(); val sub = Cursor(bytes, c.pos, c.pos + len); c.pos += len
                    partitions.add(parsePartition(sub))
                }
                else -> skip(c, wire)
            }
        }
        return blockSize to partitions
    }

    private fun parsePartition(c: Cursor): Partition {
        var name = ""; var size = 0L; val ops = ArrayList<Operation>()
        while (c.pos < c.end) {
            val tag = varint(c); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 2 -> { val len = varint(c).toInt(); name = String(c.b, c.pos, len); c.pos += len }
                field == 8 && wire == 2 -> { val len = varint(c).toInt(); ops.add(parseOp(Cursor(c.b, c.pos, c.pos + len))); c.pos += len }
                field == 9 && wire == 2 -> { val len = varint(c).toInt(); size = parseInfoSize(Cursor(c.b, c.pos, c.pos + len)); c.pos += len }
                else -> skip(c, wire)
            }
        }
        return Partition(name, size, ops)
    }

    private fun parseOp(c: Cursor): Operation {
        var type = -1; var dataOffset = 0L; var dataLength = 0L; val dst = ArrayList<Extent>()
        while (c.pos < c.end) {
            val tag = varint(c); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 0 -> type = varint(c).toInt()
                field == 2 && wire == 0 -> dataOffset = varint(c)
                field == 3 && wire == 0 -> dataLength = varint(c)
                field == 6 && wire == 2 -> { val len = varint(c).toInt(); dst.add(parseExtent(Cursor(c.b, c.pos, c.pos + len))); c.pos += len }
                else -> skip(c, wire)
            }
        }
        return Operation(type, dataOffset, dataLength, dst)
    }

    private fun parseExtent(c: Cursor): Extent {
        var start = 0L; var num = 0L
        while (c.pos < c.end) {
            val tag = varint(c); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 0 -> start = varint(c)
                field == 2 && wire == 0 -> num = varint(c)
                else -> skip(c, wire)
            }
        }
        return Extent(start, num)
    }

    private fun parseInfoSize(c: Cursor): Long {
        var size = 0L
        while (c.pos < c.end) {
            val tag = varint(c); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 0 -> size = varint(c)
                else -> skip(c, wire)
            }
        }
        return size
    }
}
