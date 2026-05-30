package com.sparkynox.lumix.helper

import android.content.Context
import android.os.Build
import java.io.File

object YtDlpInstaller {

    fun install(context: Context) {
        val binary = getBinaryFile(context)
        if (!binary.exists()) copyFromAssets(context, binary)
        binary.setExecutable(true, false)
    }

    fun getBinaryFile(context: Context): File {
        return File(context.filesDir, "yt-dlp")
    }

    private fun copyFromAssets(context: Context, dest: File) {
        val assetName = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "yt-dlp_arm64"
            else -> "yt-dlp_armv7"
        }
        try {
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.setExecutable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
