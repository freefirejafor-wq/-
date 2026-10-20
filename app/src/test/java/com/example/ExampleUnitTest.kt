package com.example

import org.junit.Assert.*
import com.example.model.SportType
import org.junit.Test
import com.example.util.DateTimeUtil
import com.example.util.MatchDateWindow
import com.example.model.MatchStatus
import com.example.repository.cricketStatusFromCode
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
  fun matchWindowIncludesTwoDaysBeforeAndAfterInDhaka() {
    val now = utc("2026-10-19T06:00:00Z")
    assertFalse(MatchDateWindow.contains(utc("2026-10-16T17:59:59Z"), now))
    assertTrue(MatchDateWindow.contains(utc("2026-10-16T18:00:00Z"), now))
    assertTrue(MatchDateWindow.contains(utc("2026-10-21T17:59:59Z"), now))
    assertFalse(MatchDateWindow.contains(utc("2026-10-21T18:00:00Z"), now))
  }

  @Test
  fun espnDatesCoverTheDhakaWindowAndUtcBoundary() {
    val now = utc("2026-10-19T06:00:00Z")
    assertEquals(
      listOf("20261016", "20261017", "20261018", "20261019", "20261020", "20261021"),
      MatchDateWindow.espnQueryDates(now)
    )
  }

  @Test
  fun cricketLiveFeedStatusUsesStartTimeToSeparateUpcomingFromLive() {
    val now = utc("2026-10-20T01:00:00Z")
    assertEquals(
      MatchStatus.UPCOMING,
      cricketStatusFromCode(1, startTimeEpochMs = utc("2026-10-20T13:30:00Z"), nowMs = now)
    )
    assertEquals(
      MatchStatus.LIVE,
      cricketStatusFromCode(1, startTimeEpochMs = utc("2026-10-20T00:59:59Z"), nowMs = now)
    )
  }

  @Test
  fun pastUpcomingTimeDoesNotDisplayAsLive() {
    val now = utc("2026-10-20T01:00:00Z")
    assertEquals("Status update pending", DateTimeUtil.calculateStartsIn(now - 1, now))
  }

  private fun utc(value: String): Long = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    Locale.US
  ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time


  @Test
  fun cricketProviderStatusCodesUseTheCorrectFilterBuckets() {
    assertEquals(MatchStatus.UPCOMING, cricketStatusFromCode(0))
    assertEquals(MatchStatus.LIVE, cricketStatusFromCode(1))
    assertEquals(MatchStatus.FINISHED, cricketStatusFromCode(2))
    assertEquals(MatchStatus.FINISHED, cricketStatusFromCode(3))
    assertEquals(MatchStatus.UPCOMING, cricketStatusFromCode(-1, MatchStatus.UPCOMING))
    assertNull(cricketStatusFromCode(-1))
  }

}
