package com.example

import com.example.model.StreamFormat
import com.example.repository.formatAdminDateTime
import com.example.repository.parseAdminDateTime
import com.example.repository.parseAdminStreamSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AdminInputParserTest {

    @Test
    fun `parses multiple named HLS and DASH sources`() {
        val sources = parseAdminStreamSources(
            """
                HD | 720p | https://example.com/live.m3u8
                Backup | 1080p | https://example.com/backup.mpd
            """.trimIndent()
        )

        assertEquals(2, sources.size)
        assertEquals("HD", sources[0].name)
        assertEquals(StreamFormat.HLS, sources[0].format)
        assertEquals(StreamFormat.DASH, sources[1].format)
    }

    @Test
    fun `rejects non-http stream urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAdminStreamSources("Local stream | file:///tmp/stream.m3u8")
        }
    }

    @Test
    fun `round trips Dhaka match time`() {
        val epochMs = parseAdminDateTime("2026-10-20 12:30")

        assertNotNull(epochMs)
        assertEquals("2026-10-20 12:30", formatAdminDateTime(epochMs!!))
        assertNull(parseAdminDateTime("2026-99-99 99:99"))
    }
}
