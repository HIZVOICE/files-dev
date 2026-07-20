package com.hyperfiles.manager

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun showDevTools(c: Context): Boolean = p(c).getBoolean("show_dev_tools", true)
    fun setShowDevTools(c: Context, v: Boolean) = p(c).edit().putBoolean("show_dev_tools", v).apply()

    // "system" | "light" | "dark" | "amoled" | "glass"
    fun themeMode(c: Context): String = p(c).getString("theme_mode", "system") ?: "system"
    fun setThemeMode(c: Context, v: String) = p(c).edit().putString("theme_mode", v).apply()

    fun liveNotify(c: Context): Boolean = p(c).getBoolean("live_notify", true)
    fun setLiveNotify(c: Context, v: Boolean) = p(c).edit().putBoolean("live_notify", v).apply()

    fun useRecycleBin(c: Context): Boolean = p(c).getBoolean("recycle_bin", true)
    fun setUseRecycleBin(c: Context, v: Boolean) = p(c).edit().putBoolean("recycle_bin", v).apply()

    fun autoPermDone(c: Context): Boolean = p(c).getBoolean("auto_perm_done", false)
    fun setAutoPermDone(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_perm_done", v).apply()
}
