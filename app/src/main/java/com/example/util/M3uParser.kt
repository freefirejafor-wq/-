package com.example.util

import com.example.model.M3uChannel
import com.example.model.StreamFormat
import java.util.UUID

object M3uParser {

    fun parse(content: String): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        val lines = content.lines()

        var currentName: String? = null
        var currentGroup = "General"
        var currentLogo: String? = null

        val tvgNameRegex = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE)
        val tvgLogoRegex = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
        val groupTitleRegex = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE)

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                // Extract group-title
                val groupMatch = groupTitleRegex.find(line)
                currentGroup = groupMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "General"

                // Extract tvg-logo
                val logoMatch = tvgLogoRegex.find(line)
                currentLogo = logoMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

                // Extract channel title after last comma
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < line.length - 1) {
                    currentName = line.substring(commaIndex + 1).trim()
                } else {
                    val nameMatch = tvgNameRegex.find(line)
                    currentName = nameMatch?.groupValues?.get(1) ?: "Live Stream"
                }
            } else if (!line.startsWith("#")) {
                // This line is the stream URL
                if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtmp://")) {
                    val name = currentName ?: "Channel ${channels.size + 1}"
                    val format = when {
                        line.contains(".mpd", ignoreCase = true) -> StreamFormat.DASH
                        line.contains(".m3u8", ignoreCase = true) -> StreamFormat.HLS
                        else -> StreamFormat.PROGRESSIVE
                    }

                    channels.add(
                        M3uChannel(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            group = currentGroup,
                            logoUrl = currentLogo,
                            streamUrl = line,
                            format = format
                        )
                    )
                }
                currentName = null
                currentGroup = "General"
                currentLogo = null
            }
        }

        return channels
    }
}
