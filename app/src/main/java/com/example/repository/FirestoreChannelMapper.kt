package com.example.repository

import com.example.model.M3uChannel
import com.example.model.StreamFormat
import com.example.model.StreamSource
import java.util.Locale

data class FirestoreChannelRecord(
    val channel: M3uChannel,
    val sortOrder: Long
)

object FirestoreChannelMapper {
    fun fromDocument(
        id: String,
        fields: Map<String, Any?>,
        includeInactive: Boolean = false
    ): FirestoreChannelRecord? {
        val isActive = fields["isActive"] as? Boolean ?: false
        if (!isActive && !includeInactive) return null

        val name = (fields["name"] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val sources = (fields["streams"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val source = item as? Map<*, *> ?: return@mapNotNull null
                val url = (source["streamUrl"] as? String)?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                StreamSource(
                    id = source["id"] as? String ?: "stream_${url.hashCode()}",
                    name = source["name"] as? String ?: name,
                    qualityLabel = source["qualityLabel"] as? String
                        ?: source["quality"] as? String
                        ?: "LIVE",
                    streamUrl = url,
                    format = parseFormat(source["format"] as? String),
                    bitrateInfo = source["bitrateInfo"] as? String ?: "",
                    isDemo = source["isDemo"] as? Boolean ?: false
                )
            }
        val streamUrl = (fields["streamUrl"] as? String)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: sources.firstOrNull()?.streamUrl
            ?: return null
        val category = (fields["category"] as? String)?.trim().orEmpty().ifBlank { "General" }
        val logoUrl = (fields["logoUrl"] as? String)?.trim()?.takeIf(String::isNotEmpty)
        val format = parseFormat(fields["format"] as? String)
        val primarySources = sources.ifEmpty {
            listOf(
                StreamSource(
                    id = "primary_$id",
                    name = name,
                    qualityLabel = (fields["quality"] as? String)?.ifBlank { null }
                        ?: format.name,
                    streamUrl = streamUrl,
                    format = format,
                    isDemo = false
                )
            )
        }
        val sortOrder = (fields["sortOrder"] as? Number)?.toLong() ?: Long.MAX_VALUE

        return FirestoreChannelRecord(
            channel = M3uChannel(
                id = id,
                name = name,
                group = category,
                logoUrl = logoUrl,
                streamUrl = streamUrl,
                format = format,
                streams = primarySources,
                enabled = isActive,
                displayOrder = sortOrder.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ),
            sortOrder = sortOrder
        )
    }

    private fun parseFormat(value: String?): StreamFormat =
        when (value?.trim()?.uppercase(Locale.ROOT)) {
            "DASH" -> StreamFormat.DASH
            "PROGRESSIVE" -> StreamFormat.PROGRESSIVE
            else -> StreamFormat.HLS
        }
}
