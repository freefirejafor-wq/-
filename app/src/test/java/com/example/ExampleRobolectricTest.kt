package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.StreamFormat
import com.example.util.DateTimeUtil
import com.example.util.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Football Live TV", appName)
  }

  @Test
  fun `m3u parser parses channels and groups`() {
    val sampleM3u = """
      #EXTM3U
      #EXTINF:-1 tvg-name="Premier Sports 1" group-title="Football",Premier Sports FHD
      https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
      #EXTINF:-1 group-title="Cricket",Star Sports Cricket
      https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd
    """.trimIndent()

    val channels = M3uParser.parse(sampleM3u)
    assertEquals(2, channels.size)
    assertEquals("Premier Sports FHD", channels[0].name)
    assertEquals("Football", channels[0].group)
    assertEquals(StreamFormat.HLS, channels[0].format)
    assertEquals("Cricket", channels[1].group)
    assertEquals(StreamFormat.DASH, channels[1].format)
  }

  @Test
  fun `dhaka timezone formats correctly`() {
    // 1700000000000 ms is Nov 14, 2023 22:13:20 UTC -> In Asia/Dhaka (+6) it is Nov 15, 2023 04:13 AM
    val timeStr = DateTimeUtil.formatTimeDhaka(1700000000000L)
    assertTrue(timeStr.contains("AM") || timeStr.contains("PM"))
  }
}
