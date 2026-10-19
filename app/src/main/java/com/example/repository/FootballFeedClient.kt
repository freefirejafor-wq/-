package com.example.repository

import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.util.MatchDateWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Reads ESPN's public global soccer scoreboard for club and international fixtures. */
internal class FootballFeedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchFixtures(): Result<List<MatchFixture>> {
        return try {
            val nowMs = System.currentTimeMillis()
            val dates = MatchDateWindow.espnQueryDates(nowMs)
            val semaphore = Semaphore(4)
            val results = coroutineScope {
                dates.map { date ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            capture { fetchScoreboard(date) }
                        }
                    }
                }.awaitAll()
            }
            val successfulDays = results.filter { it.isSuccess }
            if (successfulDays.isEmpty()) {
                throw IOException("ESPN football scoreboard is temporarily unavailable.")
            }

            val fixtures = successfulDays
                .flatMap { it.getOrNull().orEmpty() }
                .filter { MatchDateWindow.contains(it.startTimeEpochMs, nowMs) }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<MatchFixture> {
                        when (it.status) {
                            MatchStatus.LIVE -> 0
                            MatchStatus.UPCOMING -> 1
                            MatchStatus.FINISHED -> 2
                            MatchStatus.ALL -> 3
                        }
                    }.thenBy {
                        if (it.status == MatchStatus.FINISHED) -it.startTimeEpochMs else it.startTimeEpochMs
                    }
                )
            Result.success(fixtures)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetchScoreboard(date: String): List<MatchFixture> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SCOREBOARD_URL + "?dates=" + date + "&limit=500")
            .header("Accept", "application/json")
            .header("User-Agent", "FootballLiveTV/7.3 (Android)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("ESPN scoreboard returned HTTP " + response.code + ".")
            }
            val events = JSONObject(body).optJSONArray("events") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until events.length()) {
                    val event = events.optJSONObject(index) ?: continue
                    parseEvent(event)?.let(::add)
                }
            }
        }
    }

    private fun parseEvent(event: JSONObject): MatchFixture? {
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val competitors = competition.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (index in 0 until competitors.length()) {
            val competitor = competitors.optJSONObject(index) ?: continue
            when (text(competitor, "homeAway")?.lowercase(Locale.US)) {
                "home" -> home = competitor
                "away" -> away = competitor
            }
        }
        val homeCompetitor = home ?: return null
        val awayCompetitor = away ?: return null
        val homeTeam = homeCompetitor.optJSONObject("team") ?: return null
        val awayTeam = awayCompetitor.optJSONObject("team") ?: return null
        val homeName = firstText(homeTeam, "displayName", "name") ?: return null
        val awayName = firstText(awayTeam, "displayName", "name") ?: return null
        val eventId = text(event, "id") ?: return null
        val startTime = parseTimestamp(
            firstText(competition, "startDate", "date") ?: text(event, "date")
        ) ?: return null

        val statusObject = competition.optJSONObject("status") ?: event.optJSONObject("status")
        val statusType = statusObject?.optJSONObject("type")
        val state = text(statusType, "state")?.lowercase(Locale.US).orEmpty()
        val matchStatus = when (state) {
            "in" -> MatchStatus.LIVE
            "post" -> MatchStatus.FINISHED
            else -> MatchStatus.UPCOMING
        }
        val liveCounter = if (matchStatus == MatchStatus.LIVE) {
            firstText(statusType, "displayClock", "shortDetail")?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val notes = competition.optJSONArray("notes")
        val firstNote = notes?.optJSONObject(0)
        val season = event.optJSONObject("season")
        val seasonLabel = firstText(season, "name")
            ?: text(season, "slug")?.let(::humanizeSeasonSlug)
        val league = firstText(competition, "altGameNote")
            ?: firstText(firstNote, "headline")
            ?: seasonLabel
            ?: "Football"

        return MatchFixture(
            id = "espn_" + eventId,
            title = homeName + " vs " + awayName,
            sport = SportType.FOOTBALL,
            league = "FOOTBALL | " + league,
            team1Name = homeName,
            team1LogoUrl = text(homeTeam, "logo"),
            team1Abbr = text(homeTeam, "abbreviation") ?: abbreviation(homeName),
            team2Name = awayName,
            team2LogoUrl = text(awayTeam, "logo"),
            team2Abbr = text(awayTeam, "abbreviation") ?: abbreviation(awayName),
            startTimeEpochMs = startTime,
            status = matchStatus,
            liveCounter = liveCounter,
            team1Score = text(homeCompetitor, "score"),
            team2Score = text(awayCompetitor, "score"),
            streams = emptyList(),
            isDemo = false
        )
    }

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val normalized = value
            .replace(Regex("Z$"), "+0000")
            .replace(Regex("([+-]\\d{2}):(\\d{2})$")) { match ->
                match.groupValues[1] + match.groupValues[2]
            }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mmZ"
        )
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            runCatching { parser.parse(normalized)?.time }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun humanizeSeasonSlug(slug: String): String {
        val words = slug
            .replace(Regex("^\\d{4}(?:-\\d{2})?-"), "")
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return words.joinToString(" ") { word ->
            word.replaceFirstChar { first -> first.uppercase(Locale.US) }
        }.ifBlank { "Football" }
    }

    private fun abbreviation(name: String): String = name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString("") { it.first().toString() }
        .uppercase(Locale.US)
        .ifBlank { name.take(3).uppercase(Locale.US) }

    private fun firstText(json: JSONObject?, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> text(json, key) }
    }

    private fun text(json: JSONObject?, key: String): String? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return json.opt(key)?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    companion object {
        private const val SCOREBOARD_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard"
    }
}
