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

    /**
     * Copy a normally-unreadable file (e.g. under Android/data) to a shell-writable,
     * app-readable temp dir on shared storage, then return the readable copy.
     *
     * The app's private cache (/data/data/<pkg>) does NOT work for the Shizuku backend,
     * which runs as the shell uid (2000) and can't write there — only root could. A
     * /sdcard temp dir is writable by the shell uid and readable by the app (which holds
     * all-files access), so it works for both Shizuku and root.
     */
    fun copyOut(source: File): File? {
        val tmp = File(StorageUtil.primaryStorage(), "FilesDevTmp").apply { mkdirs() }
        val dest = File(tmp, source.name)
        try { if (dest.exists()) dest.delete() } catch (_: Exception) {}
        val s = source.absolutePath.replace("'", "'\\''")
        val d = dest.absolutePath.replace("'", "'\\''")
        sh("cp -f '$s' '$d' && chmod 0666 '$d'")
        return if (dest.exists() && dest.length() > 0) dest else null
    }
}
