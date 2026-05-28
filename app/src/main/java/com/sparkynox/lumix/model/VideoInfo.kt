package com.sparkynox.lumix.model

data class VideoInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,           // seconds
    val thumbnailUrl: String,
    val streamUrl: String,        // best video+audio or audio-only
    val audioOnlyUrl: String?,    // for background audio mode
    val isLive: Boolean = false
)

data class HistoryItem(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val watchedAt: Long = System.currentTimeMillis()
)
