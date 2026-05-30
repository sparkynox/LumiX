package com.sparkynox.lumix.helper

import android.content.Context
import android.os.Build
import java.io.File

object YtDlpInstaller {

    fun install(context: Context) {
        val binary = getBinaryFile(context)
        if (binary.exists() && binary.length() < 1000) {
            binary.delete()
        }
        if (!binary.exists()) {
            copyFromAssets(context, binary)
        }
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
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(assetName)) {
                val fallback = if (assetName == "yt-dlp_arm64") "yt-dlp_armv7" else "yt-dlp_arm64"
                if (assetList.contains(fallback)) copyAsset(context, fallback, dest)
                return
            }
            copyAsset(context, assetName, dest)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAsset(context: Context, assetName: String, dest: File) {
        context.assets.open(assetName).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        dest.setExecutable(true, false)
    }
}