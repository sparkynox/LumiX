package com.sparkynox.lumix.helper

import android.content.Context
import android.util.Log
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

    private const val TAG = "YtDlpHelper"
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

        val cleanUrl = normalizeYoutubeUrl(url)

        val service = ServiceList.YouTube

        val extractor = service.getStreamExtractor(
            service.streamLHFactory.fromUrl(cleanUrl)
        )

        extractor.fetchPage()

        // Debug logs
        Log.d(TAG, "Clean URL: $cleanUrl")
        Log.d(TAG, "Video ID: ${extractor.id}")
        Log.d(TAG, "Title: ${extractor.name}")
        Log.d(TAG, "Uploader: ${extractor.uploaderName}")
        Log.d(TAG, "Duration: ${extractor.length}")

        val audioStreams = extractor.audioStreams
        val videoStreams = extractor.videoStreams
        val videoOnlyStreams = extractor.videoOnlyStreams

        Log.d(TAG, "Audio Streams count: ${audioStreams?.size ?: 0}")
        Log.d(TAG, "Video Streams count: ${videoStreams?.size ?: 0}")
        Log.d(TAG, "Video Only Streams count: ${videoOnlyStreams?.size ?: 0}")

        // Compatible bestStream selection (works with v0.24.1 and v0.26.2)
        val bestStream = when {
            !audioStreams.isNullOrEmpty() -> audioStreams.firstOrNull()
            !videoStreams.isNullOrEmpty() -> videoStreams.firstOrNull()
            !videoOnlyStreams.isNullOrEmpty() -> videoOnlyStreams.firstOrNull()
            else -> null
        }

        if (bestStream == null) {
            val errorMsg = """
                No playable streams found.
                Audio: ${audioStreams?.size ?: 0}
                Video: ${videoStreams?.size ?: 0}
                VideoOnly: ${videoOnlyStreams?.size ?: 0}
                URL: $cleanUrl
            """.trimIndent()
            Log.e(TAG, errorMsg)
            throw Exception(errorMsg)
        }

        val streamUrl = bestStream.content
        val videoId = extractVideoId(cleanUrl) ?: ""
        val title = extractor.name ?: "Unknown"
        val uploader = extractor.uploaderName ?: ""
        val duration = extractor.length

        val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        if (streamUrl.isNullOrEmpty()) {
            throw Exception("Stream URL is empty")
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

    fun extractVideoId(url: String): String? {
        val regex = Regex(
            "(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})"
        )
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

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
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}

object LumiXDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15000
            readTimeout = 20000
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
                outputStream.use { it.write(data) }
            }
        }

        val responseCode = conn.responseCode
        val responseBody = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        val headers: Map<String, List<String>> =
            conn.headerFields
                .filterKeys { it != null }
                .mapValues { (_, value) -> value ?: emptyList() }

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