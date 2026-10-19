package com.example.model

data class StreamSource(
    val id: String,
    val name: String,
    val qualityLabel: String, // "FHD", "HD", "SD", "4K"
    val streamUrl: String,
    val format: StreamFormat = StreamFormat.HLS,
    val bitrateInfo: String = "1080p 60fps",
    val isDemo: Boolean = true
)
