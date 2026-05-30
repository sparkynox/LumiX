package com.sparkynox.lumix.helper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sparkynox.lumix.model.StreamInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object YtDlpHelper {

    // Invidious instances — fallback agar ek kaam na kare
    private val instances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.io.lol",
        "https://yt.drgnz.club",
        "https://iv.datura.network"
    )

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: throw Exception("Invalid YouTube URL")

        var lastError = ""
        for (instance in instances) {
            try {
                return@withContext fetchFromInvidious(instance, videoId)
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                continue // try next instance
            }
        }
        throw Exception("All instances failed: $lastError")
    }

    private fun fetchFromInvidious(instance: String, videoId: String): StreamInfo {
        val apiUrl = "$instance/api/v1/videos/$videoId?fields=title,author,lengthSeconds,adaptiveFormats,formatStreams"

        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        val responseCode = conn.responseCode
        if (responseCode != 200) throw Exception("HTTP $responseCode from $instance")

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(response)

        val title = json.optString("title", "Unknown")
        val uploader = json.optString("author", "")
        val duration = json.optLong("lengthSeconds", 0L)
        val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        // Try adaptive formats first (audio only — smaller, faster)
        var streamUrl = ""
        val adaptiveFormats = json.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.getJSONObject(i)
                val type = fmt.optString("type", "")
                val url = fmt.optString("url", "")
                if (url.isNotEmpty() && (type.contains("audio/mp4") || type.contains("audio/webm"))) {
                    streamUrl = url
                    break
                }
            }
        }

        // Fallback to formatStreams (video+audio)
        if (streamUrl.isEmpty()) {
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null && formatStreams.length() > 0) {
                streamUrl = formatStreams.getJSONObject(0).optString("url", "")
            }
        }

        if (streamUrl.isEmpty()) throw Exception("No stream URL from $instance")

        return StreamInfo(
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
            Regex("(?:shorts/)([A-Za-z0-9_-]{11})"),
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