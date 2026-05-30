package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object YtDlpHelper {

    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: throw Exception("Invalid YouTube URL")

        // Try Android client first
        try {
            return@withContext fetchWithAndroidClient(videoId)
        } catch (e: Exception) {
            // Fallback to iOS client
            try {
                return@withContext fetchWithIosClient(videoId)
            } catch (e2: Exception) {
                // Last fallback — Invidious
                return@withContext fetchFromInvidious(videoId)
            }
        }
    }

    private fun fetchWithAndroidClient(videoId: String): StreamInfo {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 30)
                    put("hl", "en")
                    put("gl", "US")
                    put("utcOffsetMinutes", 0)
                })
            })
            put("params", "2AMBCgIQBg==")
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                })
            })
        }.toString()

        val conn = (URL("https://www.youtube.com/youtubei/v1/player?key=$API_KEY&prettyPrint=false")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
            setRequestProperty("X-YouTube-Client-Name", "3")
            setRequestProperty("X-YouTube-Client-Version", "19.09.37")
            setRequestProperty("Origin", "https://www.youtube.com")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 15000
        }

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code != 200) throw Exception("Android client HTTP $code")

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        return parsePlayerResponse(response, videoId)
    }

    private fun fetchWithIosClient(videoId: String): StreamInfo {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.09.3")
                    put("deviceModel", "iPhone14,3")
                    put("hl", "en")
                    put("gl", "US")
                    put("utcOffsetMinutes", 0)
                })
            })
        }.toString()

        val conn = (URL("https://www.youtube.com/youtubei/v1/player?key=$API_KEY&prettyPrint=false")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "com.google.ios.youtube/19.09.3 (iPhone14,3; U; CPU iOS 15_6 like Mac OS X)")
            setRequestProperty("X-YouTube-Client-Name", "5")
            setRequestProperty("X-YouTube-Client-Version", "19.09.3")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 15000
        }

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code != 200) throw Exception("iOS client HTTP $code")

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        return parsePlayerResponse(response, videoId)
    }

    private fun fetchFromInvidious(videoId: String): StreamInfo {
        val instances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.io.lol",
            "https://yt.drgnz.club"
        )

        var lastError = ""
        for (instance in instances) {
            try {
                val conn = (URL("$instance/api/v1/videos/$videoId")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }

                if (conn.responseCode != 200) continue

                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()

                val title = json.optString("title", "Unknown")
                val uploader = json.optString("author", "")
                val duration = json.optLong("lengthSeconds", 0L)

                var streamUrl = ""
                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        val type = fmt.optString("type", "")
                        val u = fmt.optString("url", "")
                        if (u.isNotEmpty() && type.contains("audio/mp4")) {
                            streamUrl = u
                            break
                        }
                    }
                }

                if (streamUrl.isEmpty()) {
                    val formats = json.optJSONArray("formatStreams")
                    if (formats != null && formats.length() > 0) {
                        streamUrl = formats.getJSONObject(0).optString("url", "")
                    }
                }

                if (streamUrl.isEmpty()) continue

                return StreamInfo(
                    videoId = videoId,
                    title = title,
                    uploader = uploader,
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    duration = duration,
                    streamUrl = streamUrl
                )
            } catch (e: Exception) {
                lastError = e.message ?: ""
                continue
            }
        }
        throw Exception("All sources failed: $lastError")
    }

    private fun parsePlayerResponse(response: String, videoId: String): StreamInfo {
        val json = JSONObject(response)

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status", "") ?: ""
        if (status == "ERROR" || status == "UNPLAYABLE") {
            throw Exception(playability?.optString("reason", "Unavailable") ?: "Unavailable")
        }

        val videoDetails = json.optJSONObject("videoDetails")
            ?: throw Exception("No video details")

        val title = videoDetails.optString("title", "Unknown")
        val uploader = videoDetails.optString("author", "")
        val duration = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L

        val streamingData = json.optJSONObject("streamingData")
            ?: throw Exception("No streaming data")

        var streamUrl = ""

        // Audio only formats first
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.getJSONObject(i)
                val mimeType = fmt.optString("mimeType", "")
                val u = fmt.optString("url", "")
                if (u.isNotEmpty() && mimeType.contains("audio/mp4")) {
                    streamUrl = u
                    break
                }
            }
        }

        // Fallback to video+audio formats
        if (streamUrl.isEmpty()) {
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val u = formats.getJSONObject(i).optString("url", "")
                    if (u.isNotEmpty()) { streamUrl = u; break }
                }
            }
        }

        if (streamUrl.isEmpty()) throw Exception("No stream URL")

        return StreamInfo(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
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