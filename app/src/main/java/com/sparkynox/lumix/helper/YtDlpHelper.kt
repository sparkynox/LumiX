package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YtDlpHelper {

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        val binary = YtDlpInstaller.getBinaryFile(context)
        if (!binary.exists()) throw Exception("yt-dlp not found — reinstall app")

        val process = ProcessBuilder(
            binary.absolutePath,
            "--dump-single-json",
            "--no-playlist",
            "--format", "bestaudio[ext=m4a]/bestaudio/best",
            "--no-warnings",
            "--no-check-certificates",
            "--extractor-retries", "3",
            url
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        // Show actual yt-dlp output in error so we can debug
        if (exitCode != 0) {
            val shortError = output.take(200)
            throw Exception("yt-dlp error: $shortError")
        }

        if (output.isEmpty() || !output.trimStart().startsWith("{")) {
            val shortOutput = output.take(200)
            throw Exception("Bad output: $shortOutput")
        }

        val json = try {
            JSONObject(output)
        } catch (e: Exception) {
            throw Exception("JSON parse failed: ${output.take(100)}")
        }

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

    fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
               url.contains("youtu.be/") ||
               url.contains("youtube.com/shorts/") ||
               url.contains("youtube.com/v/") ||
               (url.contains("youtube.com") && url.contains("v="))
    }
}