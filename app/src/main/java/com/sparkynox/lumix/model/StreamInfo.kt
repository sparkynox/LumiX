package com.sparkynox.lumix.model

data class StreamInfo(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val duration: Long,
    val streamUrl: String
)