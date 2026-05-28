package com.sparkynox.lumix

import android.app.Application
import androidx.multidex.MultiDexApplication

class LumiXApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        // Copy yt-dlp binary from assets to internal storage on first run
        YtDlpInstaller.install(this)
    }
}
