package com.hyperfiles.manager

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Minimal shell / root helper. Runs commands via `sh -c` or `su -c` and
 * captures output. Used by the terminal, system-info screen, root-assisted
 * file reads and root saves.
 */
object RootShell {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok get() = exitCode == 0
    }

    private val suPaths = listOf(
        "/sbin/su", "/system/bin/su", "/system/xbin/su", "/su/bin/su",
        "/data/adb/su/bin/su", "/data/adb/magisk/su", "/data/local/xbin/su", "/data/local/bin/su"
    )

    @Volatile private var cachedRoot: Boolean? = null

    /** Fast check (no su spawn): does a su binary exist? Used to gate root UI. */
    fun suBinaryPresent(): Boolean =
        suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }

    fun isRootAvailable(force: Boolean = false): Boolean {
        cachedRoot?.let { if (!force) return it }
        val present = suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }
        val result = if (present) {
            runCatching { sh("id", root = true).ok }.getOrDefault(false)
        } else false
        cachedRoot = result
        return result
    }

    fun exec(cmd: List<String>, workingDir: File? = null): Result {
        return try {
            val pb = ProcessBuilder(cmd)
            if (workingDir != null && workingDir.isDirectory) pb.directory(workingDir)
            val proc = pb.start()
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread { readInto(proc.inputStream.bufferedReader(), out) }
            val tErr = Thread { readInto(proc.errorStream.bufferedReader(), err) }
            tOut.start(); tErr.start()
            val code = proc.waitFor()
            tOut.join(2000); tErr.join(2000)
            Result(code, out.toString(), err.toString())
        } catch (e: Exception) {
            Result(-1, "", e.message ?: "error")
        }
    }

    fun sh(command: String, root: Boolean = false, workingDir: File? = null): Result {
        val cmd = if (root) listOf("su", "-c", command) else listOf("sh", "-c", command)
        return exec(cmd, workingDir)
    }

    /** Streaming process for the interactive terminal. */
    fun start(command: String, root: Boolean, workingDir: File?): Process {
        val cmd = if (root) listOf("su", "-c", command) else listOf("sh", "-c", command)
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        if (workingDir != null && workingDir.isDirectory) pb.directory(workingDir)
        return pb.start()
    }

    /** Copy a (possibly root-only) file into a readable cache location using su. */
    fun rootCopyToCache(source: File, cacheDir: File): File? {
        val dest = File(cacheDir, "root_${source.name}")
        val r = sh("cat '${source.absolutePath}' > '${dest.absolutePath}' && chmod 666 '${dest.absolutePath}'", root = true)
        return if (r.ok && dest.exists()) dest else null
    }

    /** Overwrite a (possibly root-only) file with the given local file using su. */
    fun rootWriteFrom(localTemp: File, target: File): Boolean {
        val r = sh("cat '${localTemp.absolutePath}' > '${target.absolutePath}'", root = true)
        return r.ok
    }

    private fun readInto(reader: BufferedReader, sb: StringBuilder) {
        try {
            val buf = CharArray(4096)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { reader.close() }
        }
    }
}
