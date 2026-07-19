package com.hyperfiles.manager

import android.app.Application

class FilesDevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Theming.applyNightMode(this)
    }
}
