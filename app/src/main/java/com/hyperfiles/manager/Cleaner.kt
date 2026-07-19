package com.hyperfiles.manager

import android.content.Context
import java.io.File

object Cleaner {

    fun dirSize(f: File?): Long {
        if (f == null || !f.exists()) return 0
        if (f.isFile) return f.length()
        var total = 0L
        f.listFiles()?.forEach { total += dirSize(it) }
        return total
    }

    /** Clears the app's internal + external cache directories. Returns bytes freed. */
    fun cleanCache(ctx: Context): Long {
        var freed = 0L
        listOf(ctx.cacheDir, ctx.externalCacheDir).forEach { d ->
            if (d != null && d.exists()) {
                freed += dirSize(d)
                d.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        return freed
    }
}
