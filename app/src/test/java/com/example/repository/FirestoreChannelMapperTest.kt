package com.example.repository

import com.example.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreChannelMapperTest {
    @Test
    fun mapsActiveChannelFieldsUsedByTheAdminPanel() {
        val result = FirestoreChannelMapper.fromDocument(
            "ch_test",
            mapOf(
                "name" to "Sports HD",
                "category" to "Football",
                "format" to "DASH",
                "isActive" to true,
                "logoUrl" to "https://example.com/logo.png",
                "quality" to "HD",
                "sortOrder" to 4L,
                "streamUrl" to "https://example.com/live.mpd"
            )
        )!!

        assertEquals("ch_test", result.channel.id)
        assertEquals("Sports HD", result.channel.name)
        assertEquals("Football", result.channel.group)
        assertEquals("https://example.com/logo.png", result.channel.logoUrl)
        assertEquals("https://example.com/live.mpd", result.channel.streamUrl)
        assertEquals(StreamFormat.DASH, result.channel.format)
        assertEquals(1, result.channel.streams.size)
        assertEquals("HD", result.channel.streams.first().qualityLabel)
        assertTrue(result.channel.enabled)
        assertEquals(4, result.channel.displayOrder)
        assertEquals(4L, result.sortOrder)
    }

    @Test
    fun mapsMultipleNamedStreamsAndInactiveAdminChannels() {
        val result = FirestoreChannelMapper.fromDocument(
            "ch_multi",
            mapOf(
                "name" to "Sports",
                "category" to "Football",
                "isActive" to false,
                "sortOrder" to 9,
                "streams" to listOf(
                    mapOf(
                        "id" to "hd",
                        "name" to "HD",
                        "qualityLabel" to "720p",
                        "streamUrl" to "https://example.com/hd.m3u8",
                        "format" to "HLS"
                    ),
                    mapOf(
                        "id" to "backup",
                        "name" to "Backup",
                        "qualityLabel" to "DASH",
                        "streamUrl" to "https://example.com/backup.mpd",
                        "format" to "DASH"
                    )
                )
            ),
            includeInactive = true
        )!!

        assertEquals(2, result.channel.streams.size)
        assertEquals("Backup", result.channel.streams[1].name)
        assertEquals(StreamFormat.DASH, result.channel.streams[1].format)
        assertEquals(false, result.channel.enabled)
        assertEquals(9, result.channel.displayOrder)
    }

    @Test
    fun ignoresInactiveDocuments() {
        assertNull(
            FirestoreChannelMapper.fromDocument(
                "ch_disabled",
                mapOf("isActive" to false, "name" to "Disabled", "streamUrl" to "https://example.com/a.m3u8")
            )
        )
    }

    @Test
    fun ignoresDocumentsWithoutAUsableStream() {
        assertNull(
            FirestoreChannelMapper.fromDocument(
                "ch_empty",
                mapOf("isActive" to true, "name" to "Missing URL", "streamUrl" to " ")
            )
        )
    }
}
