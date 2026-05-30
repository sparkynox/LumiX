package com.sparkynox.lumix

import androidx.multidex.MultiDexApplication
import com.sparkynox.lumix.helper.YtDlpInstaller

class LumiXApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Thread { YtDlpInstaller.install(this) }.start()
    }
}
