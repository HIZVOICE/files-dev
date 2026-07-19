package com.hyperfiles.manager

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** ZIP create/extract using the JDK's java.util.zip (available on Android). */
object ZipUtil {

    fun zip(files: List<File>, out: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            for (f in files) addToZip(zos, f, f.name)
        }
    }

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children == null || children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/")); zos.closeEntry()
            } else {
                for (c in children) addToZip(zos, c, "$entryName/${c.name}")
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun unzip(zip: File, targetDir: File) {
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val outFile = File(targetDir, e.name)
                if (!outFile.canonicalPath.startsWith(canonicalTarget)) {
                    throw SecurityException("Zip entry outside target: ${e.name}")
                }
                if (e.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }
}
