package com.sparkynox.lumix

import android.content.Context
import android.os.Build
import java.io.File

object YtDlpInstaller {

    // Call this once from Application.onCreate()
    fun install(context: Context) {
        val binary = getBinaryFile(context)
        if (!binary.exists()) {
            copyFromAssets(context, binary)
        }
        // Always make sure it's executable
        binary.setExecutable(true, false)
    }

    fun getBinaryFile(context: Context): File {
        return File(context.filesDir, "yt-dlp")
    }

    private fun copyFromAssets(context: Context, dest: File) {
        // Pick the right asset based on ABI
        val assetName = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "yt-dlp_arm64"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "yt-dlp_armv7"
            else -> "yt-dlp_arm64" // fallback
        }

        try {
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
            // If asset not found, we'll handle gracefully in YtDlpHelper
        }
    }
}
