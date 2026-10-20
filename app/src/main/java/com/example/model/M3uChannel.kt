package com.example.model

data class M3uChannel(
    val id: String,
    val name: String,
    val group: String = "General",
    val logoUrl: String? = null,
    val streamUrl: String,
    val format: StreamFormat = StreamFormat.HLS,
    val isFavorite: Boolean = false,
    val streams: List<StreamSource> = emptyList(),
    val enabled: Boolean = true,
    val displayOrder: Int = 0
)

data class M3uPlaylist(
    val name: String,
    val sourceUrl: String? = null,
    val channels: List<M3uChannel> = emptyList(),
    val importedAt: Long = System.currentTimeMillis()
)
