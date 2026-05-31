package com.sparkynox.lumix.model

data class VideoItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val duration: Long,
    val url: String
)