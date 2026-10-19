package com.example

import org.junit.Assert.*
import com.example.model.SportType
import org.junit.Test
import com.example.util.MatchDateWindow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun sportsListContainsOnlyAllCricketAndFootball() {
    assertEquals(
      setOf(SportType.ALL, SportType.CRICKET, SportType.FOOTBALL),
      SportType.values().toSet()
    )
  }

  @Test
  fun matchWindowIncludesYesterdayTodayAndTomorrowInDhaka() {
    val now = utc("2026-10-19T06:00:00Z")
    assertFalse(MatchDateWindow.contains(utc("2026-10-17T17:59:59Z"), now))
    assertTrue(MatchDateWindow.contains(utc("2026-10-17T18:00:00Z"), now))
    assertTrue(MatchDateWindow.contains(utc("2026-10-20T17:59:59Z"), now))
    assertFalse(MatchDateWindow.contains(utc("2026-10-20T18:00:00Z"), now))
  }

  @Test
  fun espnDatesCoverTheDhakaWindowAndUtcBoundary() {
    val now = utc("2026-10-19T06:00:00Z")
    assertEquals(
      listOf("20261017", "20261018", "20261019", "20261020"),
      MatchDateWindow.espnQueryDates(now)
    )
  }

  private fun utc(value: String): Long = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    Locale.US
  ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time

}
