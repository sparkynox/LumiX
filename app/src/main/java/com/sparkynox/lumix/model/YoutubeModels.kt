package com.sparkynox.lumix.model

data class YoutubeVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val url: String
)

data class YoutubeChannel(
    val id: String,
    val name: String,
    val thumbnailUrl: String
)
