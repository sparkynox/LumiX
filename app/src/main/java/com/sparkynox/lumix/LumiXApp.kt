package com.sparkynox.lumix

import androidx.multidex.MultiDexApplication
import com.sparkynox.lumix.helper.YtDlpHelper

class LumiXApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Thread { YtDlpHelper.init() }.start()
    }
}
