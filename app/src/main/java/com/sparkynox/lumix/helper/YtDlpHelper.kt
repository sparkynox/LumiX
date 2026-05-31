package com.sparkynox.lumix.helper

import android.content.Context
import com.sparkynox.lumix.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

object YtDlpHelper {

    private var initialized = false

    fun init() {
        if (!initialized) {
            NewPipe.init(LumiXDownloader)
            initialized = true
        }
    }

    suspend fun extract(
        context: Context,
        url: String
    ): StreamInfo = withContext(Dispatchers.IO) {

        init()

        // 🔁 CHANGE 1: Normalize URL (remove playlist garbage)
        val cleanUrl = normalizeYoutubeUrl(url)

        val service = ServiceList.YouTube

        val extractor = service.getStreamExtractor(
            service.streamLHFactory.fromUrl(cleanUrl)   // 🔁 use cleanUrl instead of raw url
        )

        extractor.fetchPage()

        val videoId = extractVideoId(cleanUrl) ?: ""
        val title = extractor.name ?: "Unknown"
        val uploader = extractor.uploaderName ?: ""
        val duration = extractor.length

        val thumbnail =
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val audioStreams = extractor.audioStreams

        if (audioStreams.isNullOrEmpty()) {
            throw Exception("No audio streams found")
        }

        val bestAudio = audioStreams.maxByOrNull {
            it.averageBitrate
        } ?: throw Exception("No audio stream found")

        val streamUrl = bestAudio.content

        if (streamUrl.isNullOrEmpty()) {
            throw Exception("Empty stream URL")
        }

        StreamInfo(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnail,
            duration = duration,
            streamUrl = streamUrl
        )
    }

    // 🔁 CHANGE 2: Replace extractVideoId with a single robust regex
    fun extractVideoId(url: String): String? {
        val regex = Regex(
            "(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})"
        )
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    // 🔁 CHANGE 3: New helper function to clean YouTube URLs
    fun normalizeYoutubeUrl(url: String): String {
        val videoId = extractVideoId(url)
        if (videoId.isNullOrEmpty()) {
            return url
        }
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
                url.contains("youtu.be/") ||
                url.contains("youtube.com/shorts/") ||
                url.contains("youtube.com/v/") ||
                (url.contains("youtube.com") && url.contains("v="))
    }

    fun formatDuration(seconds: Long): String {

        if (seconds <= 0) {
            return ""
        }

        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }
}

object LumiXDownloader : Downloader() {

    override fun execute(request: Request): Response {

        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {

            requestMethod = request.httpMethod()

            connectTimeout = 15000
            readTimeout = 20000

            // Android UA
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
            )

            request.headers().forEach { (key, values) ->
                values.forEach { value ->
                    setRequestProperty(key, value)
                }
            }

            request.dataToSend()?.let { data ->
                doOutput = true

                outputStream.use {
                    it.write(data)
                }
            }
        }

        val responseCode = conn.responseCode

        val responseBody = try {
            conn.inputStream
                .bufferedReader()
                .use { it.readText() }
        } catch (_: Exception) {
            conn.errorStream
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
        }

        val headers: Map<String, List<String>> =
            conn.headerFields
                .filterKeys { it != null }
                .mapValues { (_, value) ->
                    value ?: emptyList()
                }

        val responseMessage = conn.responseMessage ?: ""

        conn.disconnect()

        return Response(
            responseCode,
            responseMessage,
            headers,
            responseBody,
            request.url()
        )
    }
}