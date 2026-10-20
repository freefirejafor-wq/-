package com.example.repository

import com.example.model.M3uChannel
import com.example.model.StreamFormat
import java.util.Locale

data class FirestoreChannelRecord(
    val channel: M3uChannel,
    val sortOrder: Long
)

object FirestoreChannelMapper {
    fun fromDocument(id: String, fields: Map<String, Any?>): FirestoreChannelRecord? {
        if (fields["isActive"] != true) return null

        val name = (fields["name"] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val streamUrl = (fields["streamUrl"] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val category = (fields["category"] as? String)?.trim().orEmpty().ifBlank { "General" }
        val logoUrl = (fields["logoUrl"] as? String)?.trim()?.takeIf(String::isNotEmpty)
        val format = when ((fields["format"] as? String)?.trim()?.uppercase(Locale.ROOT)) {
            "DASH" -> StreamFormat.DASH
            "PROGRESSIVE" -> StreamFormat.PROGRESSIVE
            else -> StreamFormat.HLS
        }

        return FirestoreChannelRecord(
            channel = M3uChannel(
                id = id,
                name = name,
                group = category,
                logoUrl = logoUrl,
                streamUrl = streamUrl,
                format = format
            ),
            sortOrder = (fields["sortOrder"] as? Number)?.toLong() ?: Long.MAX_VALUE
        )
    }
}
