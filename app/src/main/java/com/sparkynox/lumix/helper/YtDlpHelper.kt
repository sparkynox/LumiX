package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YtDlpHelper {

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        val binary = YtDlpInstaller.getBinaryFile(context)
        if (!binary.exists()) throw Exception("yt-dlp not found")

        val process = ProcessBuilder(
            binary.absolutePath,
            "--dump-single-json",
            "--no-playlist",
            "--format", "bestaudio[ext=m4a]/bestaudio/best",
            "--no-warnings",
            "--no-check-certificates",
            url
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        val json = JSONObject(output)
        val videoId = json.optString("id", "")
        val title = json.optString("title", "Unknown")
        val uploader = json.optString("uploader", "")
        val thumbnail = json.optString("thumbnail", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        val duration = json.optLong("duration", 0L)
        val streamUrl = json.optString("url", "")

        if (streamUrl.isEmpty()) throw Exception("No stream URL found")

        StreamInfo(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnail,
            duration = duration,
            streamUrl = streamUrl
        )
    }

    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})"),
            Regex("(?:embed/)([A-Za-z0-9_-]{11})")
        )
        for (p in patterns) {
            val match = p.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
               url.contains("youtu.be/") ||
               url.contains("youtube.com/shorts/") ||
               url.contains("youtube.com/v/") ||
               (url.contains("youtube.com") && url.contains("v="))
    }
}