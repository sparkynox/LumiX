package com.sparkynox.lumix.api

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sparkynox.lumix.model.YoutubeVideo
import com.sparkynox.lumix.model.YoutubeChannel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YouTubeApiClient {

    private const val BASE = "https://www.googleapis.com/youtube/v3"
    // IMPORTANT: Replace with your YouTube Data API v3 key from Google Cloud Console
    // Enable: YouTube Data API v3 at console.cloud.google.com
    private const val API_KEY = "YOUR_YOUTUBE_API_KEY_HERE"

    // Get OAuth access token from the signed-in Google account
    private fun getAccessToken(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.serverAuthCode // used for server-side; for client use idToken
        // For actual API calls we use the API key for public data
        // and OAuth token for user-specific data (liked videos, subscriptions)
    }

    // ── SUBSCRIPTIONS ────────────────────────────────────────────────────────

    suspend fun getSubscriptions(context: Context, pageToken: String? = null): Pair<List<YoutubeChannel>, String?> =
        withContext(Dispatchers.IO) {
            val token = getOAuthToken(context) ?: return@withContext Pair(emptyList(), null)

            var url = "$BASE/subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical"
            if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

            val json = getWithToken(url, token)
            val channels = mutableListOf<YoutubeChannel>()
            val items = json.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
            val nextPage = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val snippet = item.getJSONObject("snippet")
                val resourceId = snippet.getJSONObject("resourceId")
                channels.add(
                    YoutubeChannel(
                        id = resourceId.getString("channelId"),
                        name = snippet.getString("title"),
                        thumbnailUrl = snippet.getJSONObject("thumbnails")
                            .optJSONObject("default")?.optString("url") ?: ""
                    )
                )
            }
            Pair(channels, nextPage)
        }

    // ── SUBSCRIPTION FEED (latest videos from subscribed channels) ───────────

    suspend fun getSubscriptionFeed(context: Context, pageToken: String? = null): Pair<List<YoutubeVideo>, String?> =
        withContext(Dispatchers.IO) {
            val token = getOAuthToken(context) ?: return@withContext Pair(emptyList(), null)

            var url = "$BASE/activities?part=snippet,contentDetails&home=true&maxResults=50"
            if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

            val json = getWithToken(url, token)
            val videos = parseVideoItems(json, fromActivities = true)
            val nextPage = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
            Pair(videos, nextPage)
        }

    // ── LIKED VIDEOS ─────────────────────────────────────────────────────────

    suspend fun getLikedVideos(context: Context, pageToken: String? = null): Pair<List<YoutubeVideo>, String?> =
        withContext(Dispatchers.IO) {
            val token = getOAuthToken(context) ?: return@withContext Pair(emptyList(), null)

            // "Liked Videos" is a special playlist — get its ID first
            val channelUrl = "$BASE/channels?part=contentDetails&mine=true"
            val channelJson = getWithToken(channelUrl, token)
            val likedPlaylistId = channelJson
                .optJSONArray("items")
                ?.optJSONObject(0)
                ?.optJSONObject("contentDetails")
                ?.optJSONObject("relatedPlaylists")
                ?.optString("likes") ?: return@withContext Pair(emptyList(), null)

            var url = "$BASE/playlistItems?part=snippet&playlistId=$likedPlaylistId&maxResults=50"
            if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

            val json = getWithToken(url, token)
            val videos = parsePlaylistItems(json)
            val nextPage = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
            Pair(videos, nextPage)
        }

    // ── SEARCH ───────────────────────────────────────────────────────────────

    suspend fun search(query: String, pageToken: String? = null): Pair<List<YoutubeVideo>, String?> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            var url = "$BASE/search?part=snippet&type=video&q=$q&maxResults=20&key=$API_KEY"
            if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

            val json = getJson(url)
            val videos = mutableListOf<YoutubeVideo>()
            val items = json.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
            val nextPage = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.optJSONObject("id")?.optString("videoId") ?: continue
                val snippet = item.getJSONObject("snippet")
                videos.add(buildVideo(id, snippet))
            }
            Pair(videos, nextPage)
        }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private fun parseVideoItems(json: JSONObject, fromActivities: Boolean): List<YoutubeVideo> {
        val videos = mutableListOf<YoutubeVideo>()
        val items = json.optJSONArray("items") ?: return emptyList()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.optJSONObject("snippet") ?: continue
            val videoId = if (fromActivities) {
                item.optJSONObject("contentDetails")
                    ?.optJSONObject("upload")
                    ?.optString("videoId") ?: continue
            } else {
                item.optString("id")
            }
            videos.add(buildVideo(videoId, snippet))
        }
        return videos
    }

    private fun parsePlaylistItems(json: JSONObject): List<YoutubeVideo> {
        val videos = mutableListOf<YoutubeVideo>()
        val items = json.optJSONArray("items") ?: return emptyList()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.optJSONObject("snippet") ?: continue
            val videoId = snippet.optJSONObject("resourceId")?.optString("videoId") ?: continue
            videos.add(buildVideo(videoId, snippet))
        }
        return videos
    }

    private fun buildVideo(videoId: String, snippet: JSONObject): YoutubeVideo {
        val thumbs = snippet.optJSONObject("thumbnails")
        val thumb = thumbs?.optJSONObject("high")?.optString("url")
            ?: thumbs?.optJSONObject("medium")?.optString("url")
            ?: thumbs?.optJSONObject("default")?.optString("url")
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return YoutubeVideo(
            id = videoId,
            title = snippet.optString("title", "Unknown"),
            channelName = snippet.optString("channelTitle", ""),
            thumbnailUrl = thumb,
            publishedAt = snippet.optString("publishedAt", ""),
            url = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun getOAuthToken(context: Context): String? {
        // We use the Google Sign-In account's ID token for OAuth calls
        // In production, exchange this for an access token via server
        // For simplicity here we use the account auth token directly
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                account?.account ?: return null,
                "oauth2:https://www.googleapis.com/auth/youtube.readonly"
            )
        } catch (e: Exception) {
            Log.e("YouTubeApi", "Token error: ${e.message}")
            null
        }
    }

    private fun getWithToken(urlStr: String, token: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(response)
    }

    private fun getJson(urlStr: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(response)
    }
}
