package com.hyperfiles.manager

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

object Theming {

    fun applyNightMode(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.themeMode(ctx)) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "amoled", "glass" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Call before setContentView. Applies the pure-black theme when AMOLED is selected. */
    fun applyActivityTheme(activity: AppCompatActivity) {
        if (Prefs.themeMode(activity) == "amoled") {
            activity.setTheme(R.style.Theme_HyperFiles_Amoled)
        }
    }
}
