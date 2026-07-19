package com.hyperfiles.manager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Thin wrapper over the Shizuku API. Shizuku lets an app run commands with
 * ADB (shell) privileges through a user-started Shizuku service — enough to
 * read the OS-restricted Android/data and Android/obb trees that scoped
 * storage blocks on Android 11+.
 *
 * All calls are defensive: if Shizuku isn't installed/running the binder ping
 * throws, so everything is wrapped and degrades to "unavailable".
 */
object ShizukuAccess {

    const val REQ = 4211

    /** True if the Shizuku service is installed and currently running (binder alive). */
    fun installedOrRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    /** True if the service is running AND has granted us permission. */
    fun granted(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    /** Request the runtime permission; [onResult] fires with the grant outcome. */
    fun request(onResult: (Boolean) -> Unit) {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) { onResult(false); return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { onResult(true); return }
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode == REQ) {
                        Shizuku.removeRequestPermissionResultListener(this)
                        onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQ)
        } catch (e: Throwable) {
            onResult(false)
        }
    }

    /**
     * Run `sh -c command` inside the Shizuku (ADB-privileged) process and capture output.
     * Uses the library-restricted Shizuku.newProcess via reflection so we don't trip its
     * @RestrictTo lint; returns a non-zero exit Result on any failure.
     */
    fun exec(command: String): RootShell.Result {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }
            val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            RootShell.Result(code, out, err)
        } catch (e: Throwable) {
            RootShell.Result(-1, "", e.message ?: "shizuku error")
        }
    }
}
