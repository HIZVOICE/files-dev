package com.hyperfiles.manager

import java.io.File

/**
 * Chooses an elevated shell backend for reaching storage the normal File API
 * can't — Shizuku (ADB privileges) when the user has granted it, otherwise a
 * root `su` shell when a su binary is present. Everything routes through here so
 * the browser doesn't care which one is in play.
 */
object Elevated {

    enum class Backend { NONE, SHIZUKU, ROOT }

    fun backend(): Backend = when {
        ShizukuAccess.granted() -> Backend.SHIZUKU
        RootShell.suBinaryPresent() -> Backend.ROOT
        else -> Backend.NONE
    }

    fun available(): Boolean = backend() != Backend.NONE

    fun label(): String = when (backend()) {
        Backend.SHIZUKU -> "Shizuku"
        Backend.ROOT -> "root"
        Backend.NONE -> "no elevated access"
    }

    fun sh(command: String): RootShell.Result = when (backend()) {
        Backend.SHIZUKU -> ShizukuAccess.exec(command)
        Backend.ROOT -> RootShell.sh(command, root = true)
        Backend.NONE -> RootShell.sh(command)
    }

    /** Copy a normally-unreadable file into a readable cache location via the elevated shell. */
    fun copyToCache(source: File, cacheDir: File): File? {
        val dest = File(cacheDir, "elev_${source.name}")
        val r = sh("cat '${source.absolutePath}' > '${dest.absolutePath}' && chmod 666 '${dest.absolutePath}'")
        return if (r.ok && dest.exists()) dest else null
    }
}
