package com.hyperfiles.manager

import java.io.File

/**
 * Returns a File that the plain java.io API can actually read.
 *
 * Archive and ROM-payload parsing use direct FileInputStream/RandomAccessFile,
 * which fail for anything outside normally-accessible storage — most notably
 * Android/data, Android/obb and root-only paths (/data, /system, …), which are
 * exactly the places this app is built to reach. When the file isn't directly
 * readable we copy it out through the elevated (Shizuku/root) backend into a
 * shared temp dir and hand back that readable copy.
 */
object Readable {

    /** Original file if readable; else an elevated copy; else null. */
    fun resolve(file: File): File? = when {
        file.canRead() -> file
        Elevated.available() -> Elevated.copyOut(file)
        else -> null
    }

    /** Human-readable explanation for when [resolve] returns null. */
    fun reason(): String =
        if (Elevated.available())
            "Couldn't read this file even with ${Elevated.label()} — it may be missing or locked."
        else
            "This file is in a restricted location (e.g. Android/data or a system partition). " +
                "Grant Shizuku or root access in the app to open it."
}
