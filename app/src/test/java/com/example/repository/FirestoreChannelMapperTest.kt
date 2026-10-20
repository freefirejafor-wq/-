package com.example.repository

import com.example.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(4L, result.sortOrder)
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
