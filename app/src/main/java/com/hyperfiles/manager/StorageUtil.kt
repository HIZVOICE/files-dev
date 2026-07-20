package com.hyperfiles.manager

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.system.OsConstants
import android.text.format.DateUtils
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StorageUtil {

    fun primaryStorage(): File = Environment.getExternalStorageDirectory()

    fun downloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * On Android 11+ this is the "All files access" (MANAGE_EXTERNAL_STORAGE) grant.
     * On Android 10 and below there is no such thing — we must actually hold the
     * READ_EXTERNAL_STORAGE runtime permission, so check it (returning `true`
     * unconditionally previously meant the app never requested it and couldn't read files).
     */
    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var i = 0
        while (size >= 1024 && i < units.size - 1) {
            size /= 1024; i++
        }
        val df = DecimalFormat(if (i == 0) "0" else "0.00")
        return "${df.format(size)} ${units[i]}"
    }

    fun formatDate(ts: Long): String =
        DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    /** HyperOS-style stamp: "HH:mm" if the file changed today, otherwise "MM/dd/yyyy". */
    fun shortStamp(ts: Long): String {
        if (ts <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "MM/dd/yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))
    }

    /** Medium date like "Nov 9, 2025" (Files-by-Google media style). */
    fun mediumDate(ts: Long): String {
        if (ts <= 0) return ""
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))
    }

    data class Usage(val total: Long, val used: Long, val free: Long) {
        val percentUsed: Int get() = if (total <= 0) 0 else ((used * 100) / total).toInt()
    }

    fun usageOf(path: File): Usage = try {
        val stat = StatFs(path.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        Usage(total, total - free, free)
    } catch (e: Exception) {
        Usage(0, 0, 0)
    }

    /** Real Unix permission string like "drwxr-xr-x", or "" if unavailable. */
    fun permString(file: File): String = try {
        modeToString(android.system.Os.stat(file.absolutePath).st_mode)
    } catch (e: Exception) {
        ""
    }

    fun permOctal(file: File): String = try {
        Integer.toOctalString(android.system.Os.stat(file.absolutePath).st_mode and 0xFFF)
    } catch (e: Exception) {
        ""
    }

    private fun modeToString(mode: Int): String {
        val sb = StringBuilder(10)
        sb.append(
            when (mode and OsConstants.S_IFMT) {
                OsConstants.S_IFDIR -> 'd'; OsConstants.S_IFLNK -> 'l'; OsConstants.S_IFBLK -> 'b'
                OsConstants.S_IFCHR -> 'c'; OsConstants.S_IFIFO -> 'p'; OsConstants.S_IFSOCK -> 's'; else -> '-'
            }
        )
        sb.append(if (mode and OsConstants.S_IRUSR != 0) 'r' else '-')
        sb.append(if (mode and OsConstants.S_IWUSR != 0) 'w' else '-')
        sb.append(if (mode and OsConstants.S_IXUSR != 0) 'x' else '-')
        sb.append(if (mode and OsConstants.S_IRGRP != 0) 'r' else '-')
        sb.append(if (mode and OsConstants.S_IWGRP != 0) 'w' else '-')
        sb.append(if (mode and OsConstants.S_IXGRP != 0) 'x' else '-')
        sb.append(if (mode and OsConstants.S_IROTH != 0) 'r' else '-')
        sb.append(if (mode and OsConstants.S_IWOTH != 0) 'w' else '-')
        sb.append(if (mode and OsConstants.S_IXOTH != 0) 'x' else '-')
        return sb.toString()
    }
}
