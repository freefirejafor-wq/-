package com.example.repository

import com.example.model.StreamFormat
import com.example.model.StreamSource
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses one stream per line. Supported forms:
 *   https://example.com/live.m3u8
 *   HD | https://example.com/live.m3u8
 *   Sports HD | 1080p | https://example.com/live.m3u8
 */
fun parseAdminStreamSources(input: String): List<StreamSource> {
    return input.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, line ->
            val parts = line.split("|", limit = 3).map { it.trim() }
            val name: String
            val quality: String
            val url: String
            when (parts.size) {
                1 -> {
                    url = parts[0]
                    name = "Stream ${index + 1}"
                    quality = inferredQuality(url)
                }
                2 -> {
                    name = parts[0]
                    url = parts[1]
                    quality = inferredQuality(url)
                }
                else -> {
                    name = parts[0]
                    quality = parts[1].ifBlank { inferredQuality(parts[2]) }
                    url = parts[2]
                }
            }
            require(name.isNotBlank()) { "Each stream needs a name." }
            require(url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)) {
                "Stream URLs must start with http:// or https://."
            }
            StreamSource(
                id = "admin_stream_${index + 1}",
                name = name,
                qualityLabel = quality.ifBlank { inferredQuality(url) },
                streamUrl = url,
                format = inferFormat(url),
                isDemo = false
            )
        }
        .toList()
}

private fun inferredQuality(url: String): String = when {
    url.contains(".mpd", ignoreCase = true) -> "DASH"
    url.contains(".m3u8", ignoreCase = true) -> "HLS"
    else -> "LIVE"
}

private fun inferFormat(url: String): StreamFormat = when {
    url.contains(".mpd", ignoreCase = true) -> StreamFormat.DASH
    url.contains(".m3u8", ignoreCase = true) -> StreamFormat.HLS
    else -> StreamFormat.PROGRESSIVE
}

fun formatAdminDateTime(epochMs: Long): String = dhakaDateFormatter().format(Date(epochMs))

fun parseAdminDateTime(value: String): Long? {
    val formatter = dhakaDateFormatter()
    val position = ParsePosition(0)
    val date = formatter.parse(value.trim(), position) ?: return null
    return date.time.takeIf { position.index == value.trim().length }
}

private fun dhakaDateFormatter() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
    isLenient = false
    timeZone = TimeZone.getTimeZone("Asia/Dhaka")
}
