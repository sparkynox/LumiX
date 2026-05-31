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

    suspend fun extract(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
        init()

        val service = ServiceList.YouTube
        val extractor = service.getStreamExtractor(
            service.streamLHFactory.fromUrl(url)
        )
        extractor.fetchPage()

        val videoId = extractVideoId(url) ?: ""
        val title = extractor.name ?: "Unknown"
        val uploader = extractor.uploaderName ?: ""
        val duration = extractor.length
        val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        // Audio only — no ads, no video, fast
        val audioStreams = extractor.audioStreams
        if (audioStreams.isNullOrEmpty()) throw Exception("No audio streams found")

        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw Exception("No audio stream")

        val streamUrl = bestAudio.content
        if (streamUrl.isNullOrEmpty()) throw Exception("Empty stream URL")

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
                "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
            )
            request.headers().forEach { (key, values) ->
                values.forEach { value -> setRequestProperty(key, value) }
            }
            if (request.dataToSend() != null) {
                doOutput = true
                outputStream.use { it.write(request.dataToSend()) }
            }
        }

        val responseCode = conn.responseCode
        val responseBody = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }

        val headers = conn.headerFields
            .filterKeys { it != null }
            .mapValues { it.value.joinToString(",") }

        conn.disconnect()
        return Response(
            responseCode,
            conn.responseMessage,
            headers,
            responseBody,
            request.url()
        )
    }
}