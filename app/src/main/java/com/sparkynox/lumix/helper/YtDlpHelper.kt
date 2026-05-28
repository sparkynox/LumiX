package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.YtDlpInstaller
import com.sparkynox.lumix.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YtDlpHelper {

    // Returns VideoInfo or throws an exception with a user-readable message
    suspend fun extract(context: Context, url: String): VideoInfo = withContext(Dispatchers.IO) {
        val binary = YtDlpInstaller.getBinaryFile(context)

        if (!binary.exists()) {
            throw Exception("yt-dlp binary not found. Please reinstall the app.")
        }

        // Run yt-dlp with JSON output, no actual download
        val process = ProcessBuilder(
            binary.absolutePath,
            "--dump-single-json",
            "--no-playlist",
            "--format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            "--no-warnings",
            url
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            // Try to pull a readable error from output
            val errorMsg = output.lines().firstOrNull { it.contains("ERROR") }
                ?: "Failed to extract video info"
            throw Exception(errorMsg)
        }

        parseJson(output)
    }

    private fun parseJson(raw: String): VideoInfo {
        val json = JSONObject(raw)

        val videoId = json.optString("id", "")
        val title = json.optString("title", "Unknown Title")
        val uploader = json.optString("uploader", "Unknown")
        val duration = json.optLong("duration", 0L)
        val thumbnail = json.optString("thumbnail", "")
        val isLive = json.optBoolean("is_live", false)

        // Get best stream URL
        val streamUrl = json.optString("url", "")

        // Try to find an audio-only format for background mode
        var audioUrl: String? = null
        val formats = json.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val fmt = formats.getJSONObject(i)
                val vcodec = fmt.optString("vcodec", "")
                val acodec = fmt.optString("acodec", "")
                val ext = fmt.optString("ext", "")
                // Audio-only format (no video codec, has audio)
                if ((vcodec == "none" || vcodec.isEmpty()) && acodec != "none" && ext == "m4a") {
                    audioUrl = fmt.optString("url")
                    break
                }
            }
        }

        return VideoInfo(
            id = videoId,
            title = title,
            uploader = uploader,
            duration = duration,
            thumbnailUrl = thumbnail,
            streamUrl = streamUrl,
            audioOnlyUrl = audioUrl,
            isLive = isLive
        )
    }

    // Extract just the video ID from various YouTube URL formats
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})"),
            Regex("(?:embed/)([A-Za-z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
