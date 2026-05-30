package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object YtDlpHelper {

    // InnerTube API — YouTube ka internal API, koi key nahi
    private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1"
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // YouTube's own public key
    private const val CLIENT_VERSION = "17.31.35"
    private const val CLIENT_NAME = "ANDROID"

    private fun innerTubeBody(extra: String = ""): String {
        return """
        {
            "context": {
                "client": {
                    "clientName": "$CLIENT_NAME",
                    "clientVersion": "$CLIENT_VERSION",
                    "androidSdkVersion": 30,
                    "hl": "en",
                    "gl": "US"
                }
            }
            $extra
        }
        """.trimIndent()
    }

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: throw Exception("Invalid YouTube URL")

        val requestBody = innerTubeBody(""", "videoId": "$videoId" """)

        val conn = (URL("$INNERTUBE_URL/player?key=$API_KEY").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "com.google.android.youtube/17.31.35 (Linux; U; Android 11) gzip")
            setRequestProperty("X-YouTube-Client-Name", "3")
            setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            doOutput = true
            connectTimeout = 10000
            readTimeout = 15000
        }

        conn.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = conn.responseCode
        if (responseCode != 200) throw Exception("InnerTube error: HTTP $responseCode")

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(response)

        // Check playability
        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status", "") ?: ""
        if (status == "ERROR" || status == "UNPLAYABLE") {
            val reason = playability?.optString("reason", "Video unavailable") ?: "Video unavailable"
            throw Exception(reason)
        }

        val videoDetails = json.optJSONObject("videoDetails")
            ?: throw Exception("No video details")

        val title = videoDetails.optString("title", "Unknown")
        val uploader = videoDetails.optString("author", "")
        val duration = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
        val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        // Get stream URLs from streamingData
        val streamingData = json.optJSONObject("streamingData")
            ?: throw Exception("No streaming data — video may be restricted")

        var streamUrl = ""

        // Try adaptive formats first (audio only — no ads, smaller)
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.getJSONObject(i)
                val mimeType = fmt.optString("mimeType", "")
                val url2 = fmt.optString("url", "")
                if (url2.isNotEmpty() && mimeType.contains("audio/mp4")) {
                    streamUrl = url2
                    break
                }
            }
        }

        // Fallback to formats (video+audio)
        if (streamUrl.isEmpty()) {
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url2 = fmt.optString("url", "")
                    if (url2.isNotEmpty()) {
                        streamUrl = url2
                        break
                    }
                }
            }
        }

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