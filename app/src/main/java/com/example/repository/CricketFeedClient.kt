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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal class CricketFeedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedTeamNames: Map<String, String> = emptyMap()

    @Volatile
    private var teamNamesFetchedAtMs: Long = 0L

    suspend fun fetchFixtures(): Result<List<MatchFixture>> {
        return try {
            val teamNames = withContext(Dispatchers.IO) {
                runCatching { fetchTeamNames() }.getOrDefault(emptyMap()).toMutableMap()
            }
            val liveResult = capture { fetchLiveFixtures(teamNames) }
            val upcomingResult = capture {
                fetchMatchList(type = 1, status = MatchStatus.UPCOMING, teamNames = teamNames)
            }
            val finishedResult = capture {
                fetchMatchList(type = 0, status = MatchStatus.FINISHED, teamNames = teamNames)
            }

            if (liveResult.isFailure && upcomingResult.isFailure && finishedResult.isFailure) {
                throw IOException("Cricket match feeds are temporarily unavailable.")
            }

            val byId = linkedMapOf<String, MatchFixture>()
            finishedResult.getOrNull().orEmpty().forEach { byId[it.id] = it }
            upcomingResult.getOrNull().orEmpty().forEach { byId[it.id] = it }
            liveResult.getOrNull().orEmpty().forEach { byId[it.id] = it }
            Result.success(
                byId.values
                    .filter { MatchDateWindow.contains(it.startTimeEpochMs) }
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
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetchLiveFixtures(teamNames: MutableMap<String, String>): List<MatchFixture> {
        val liveRoot = withContext(Dispatchers.IO) {
            requestJson(LIVE_MATCHES_URL) as? JSONObject
                ?: throw IOException("Live cricket feed returned an unexpected response.")
        }
        val candidates = liveRoot.keys().asSequence().mapNotNull { id ->
            val match = liveRoot.optJSONObject(id) ?: return@mapNotNull null
            MatchCandidate(id, match)
        }.toList()
        val semaphore = Semaphore(6)
        val results = coroutineScope {
            candidates.map { candidate ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val detailUrl = MATCH_DETAIL_URL + "?key=" + URLEncoder.encode(candidate.id, "UTF-8")
                            val detail = requestJson(detailUrl) as? JSONObject ?: return@withPermit null
                            val startTime = matchTimeMs(detail) ?: matchTimeMs(candidate.row)
                            val matchStatus = statusFromDetail(
                                detail,
                                candidate.row,
                                fallback = null,
                                startTimeEpochMs = startTime
                            ) ?: return@withPermit null
                            val names = parseSpeechNames(detail)
                            val fixture = parseFixture(
                                candidate.id,
                                candidate.row,
                                detail,
                                matchStatus,
                                teamNames + names
                            )
                            FetchedFixture(fixture, names)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        results.forEach { teamNames.putAll(it.teamNames) }
        return results.mapNotNull { it.fixture }
    }

    private suspend fun fetchMatchList(
        type: Int,
        status: MatchStatus,
        teamNames: MutableMap<String, String>
    ): List<MatchFixture> {
        val primaryResult = capture {
            withContext(Dispatchers.IO) {
                requestJson(HOME_MATCHES_URL, JSONObject().put("type", type))
            }
        }
        val fallbackResult = capture {
            withContext(Dispatchers.IO) {
                requestJson(FILTERED_MATCHES_URL, JSONObject().put("flag", 1).put("type", type))
            }
        }
        if (primaryResult.isFailure && fallbackResult.isFailure) {
            throw IOException("Cricket schedule is temporarily unavailable.")
        }

        val rows = (primaryResult.getOrNull()?.let(::flattenMatchObjects).orEmpty() +
            fallbackResult.getOrNull()?.let(::flattenMatchObjects).orEmpty())
            .distinctBy { firstText(it, "mf", "mfkey", "nf", "id") }
        val candidates = rows.mapNotNull { row ->
            val id = firstText(row, "mf", "mfkey", "nf", "id") ?: return@mapNotNull null
            val startTime = matchTimeMs(row) ?: return@mapNotNull null
            if (MatchDateWindow.contains(startTime)) MatchCandidate(id, row) else null
        }
        val semaphore = Semaphore(6)
        val results = coroutineScope {
            candidates.map { candidate ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val detailUrl = MATCH_DETAIL_URL + "?key=" + URLEncoder.encode(candidate.id, "UTF-8")
                            val detail = requestJson(detailUrl) as? JSONObject
                            val names = detail?.let(::parseSpeechNames).orEmpty()
                            val startTime = matchTimeMs(detail) ?: matchTimeMs(candidate.row)
                            val actualStatus = statusFromDetail(
                                detail,
                                candidate.row,
                                fallback = status,
                                startTimeEpochMs = startTime
                            ) ?: return@withPermit null
                            val fixture = parseFixture(
                                candidate.id,
                                candidate.row,
                                detail,
                                actualStatus,
                                teamNames + names
                            )
                            FetchedFixture(fixture, names)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            val fallbackStartTime = matchTimeMs(candidate.row)
                            val fallbackStatus = cricketStatusFromCode(
                                code = -1,
                                fallback = status,
                                startTimeEpochMs = fallbackStartTime,
                                hasFinalResult = hasFinalMatchResult(null, candidate.row)
                            ) ?: return@withPermit null
                            val fallbackFixture = parseFixture(
                                candidate.id,
                                candidate.row,
                                null,
                                fallbackStatus,
                                teamNames
                            )
                            FetchedFixture(fallbackFixture, emptyMap())
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        results.forEach { teamNames.putAll(it.teamNames) }
        return results.mapNotNull { it.fixture }
    }

    private fun parseFixture(
        id: String,
        row: JSONObject,
        detail: JSONObject?,
        status: MatchStatus,
        teamNames: Map<String, String>
    ): MatchFixture? {
        val detailTeamKeys = firstText(detail, "a")?.split('.')
        val team1Key = firstText(row, "t1f", "team1fkey")
            ?: detailTeamKeys?.getOrNull(0).orEmpty()
        val team2Key = firstText(row, "t2f", "team2fkey")
            ?: detailTeamKeys?.getOrNull(1).orEmpty()
        val format = firstText(detail, "fo") ?: firstText(row, "fo", "formats")
        if (!isInternationalFormat(format)) return null

        val team1 = firstText(row, "team1", "team1Name")
            ?: teamNames[team1Key]
            ?: team1Key.ifBlank { "Team 1" }
        val team2 = firstText(row, "team2", "team2Name")
            ?: teamNames[team2Key]
            ?: team2Key.ifBlank { "Team 2" }
        val matchNumber = firstText(row, "mn", "match_number", "matchNums")
            ?: firstText(detail, "mn")
        val startTime = matchTimeMs(detail) ?: matchTimeMs(row) ?: return null
        if (!MatchDateWindow.contains(startTime)) return null
        val league = listOfNotNull(
            "CRICKET",
            format?.takeIf { it.isNotBlank() },
            matchNumber?.takeIf { it.isNotBlank() }?.let { "Match " + it.trimStart('^') }
        ).joinToString(" | ")

        return MatchFixture(
            id = "cricket_" + id,
            title = team1 + " vs " + team2,
            sport = SportType.CRICKET,
            league = league,
            team1Name = team1,
            team1LogoUrl = teamFlagUrl(team1Key),
            team1Abbr = abbreviation(team1, team1Key),
            team2Name = team2,
            team2LogoUrl = teamFlagUrl(team2Key),
            team2Abbr = abbreviation(team2, team2Key),
            startTimeEpochMs = startTime,
            status = status,
            team1Score = formatScore(
                firstText(detail, "j") ?: firstText(row, "score1", "s1", "team1Score1"),
                firstText(row, "overs1", "o1", "team1Over1")
            ),
            team2Score = formatScore(
                firstText(detail, "k") ?: firstText(row, "score2", "s2", "team2Score1"),
                firstText(row, "overs2", "o2", "team2Over1")
            ),
            streams = emptyList(),
            isDemo = false
        )
    }

    /** The provider's match-detail status is authoritative; list endpoints mix match states. */
    private fun statusFromDetail(
        detail: JSONObject?,
        row: JSONObject?,
        fallback: MatchStatus?,
        startTimeEpochMs: Long?
    ): MatchStatus? {
        return cricketStatusFromCode(
            detail?.optInt("ms", -1) ?: -1,
            fallback,
            startTimeEpochMs,
            hasFinalResult = hasFinalMatchResult(detail, row)
        )
    }

    private fun hasFinalMatchResult(detail: JSONObject?, row: JSONObject?): Boolean {
        val detailResult = firstText(detail, "B")
        val listResult = firstText(row, "result", "res")
        return listOfNotNull(detailResult, listResult).any(FINAL_RESULT_PATTERN::containsMatchIn)
    }

    private fun isInternationalFormat(format: String?): Boolean {
        if (format.isNullOrBlank()) return false
        val normalized = format.trim().uppercase(Locale.US)
        if (normalized.contains("FIRST CLASS") || normalized.contains("MULTI DAY")) return false
        return INTERNATIONAL_FORMAT_PATTERN.matcher(normalized).find()
    }

    private fun fetchTeamNames(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (cachedTeamNames.isNotEmpty() && now - teamNamesFetchedAtMs < TEAM_NAME_CACHE_MS) {
            return cachedTeamNames
        }

        val html = requestText(SCHEDULE_PAGE_URL)
        val names = mutableMapOf<String, String>()
        val tags = IMAGE_TAG_PATTERN.matcher(html)
        while (tags.find()) {
            val tag = tags.group()
            val code = TEAM_IMAGE_PATTERN.matcher(tag)
            val name = TEAM_ALT_PATTERN.matcher(tag)
            if (code.find() && name.find()) {
                val teamKey = code.group(1)
                val teamName = decodeHtml(name.group(1)).trim()
                if (teamKey.isNotBlank() && teamName.isNotBlank()) {
                    names[teamKey] = teamName
                }
            }
        }
        cachedTeamNames = names
        teamNamesFetchedAtMs = now
        return names
    }

    private fun parseSpeechNames(detail: JSONObject): Map<String, String> {
        val speechNames = detail.optJSONObject("speech_names") ?: return emptyMap()
        val names = mutableMapOf<String, String>()
        val keys = speechNames.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            text(speechNames, key)?.let { names[key] = it }
        }
        return names
    }

    private fun matchTimeMs(json: JSONObject?): Long? {
        if (json == null) return null
        val epoch = firstLong(json, "mt", "t", "startTimeEpochMs", "ti", "ed")
        if (epoch > 0L) return epoch
        val rawDate = firstText(json, "dt", "date") ?: return null
        val patterns = listOf("yyyy/MM/dd", "MM/dd/yyyy", "yyyy-MM-dd")
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            }
            runCatching { parser.parse(rawDate)?.time }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun requestJson(url: String, body: JSONObject? = null): Any {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (body == null) get() else post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Cricket API returned HTTP " + response.code + ".")
            }
            return JSONTokener(text).nextValue()
        }
    }

    private fun requestText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) FootballLiveTV")
            .header("Accept", "text/html")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Cricket schedule returned HTTP " + response.code + ".")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun flattenMatchObjects(payload: Any): List<JSONObject> {
        val output = mutableListOf<JSONObject>()
        fun add(value: Any?) {
            val item = value as? JSONObject ?: return
            val nested = item.optJSONArray("m")
            if (nested == null) {
                output += item
            } else {
                for (index in 0 until nested.length()) {
                    (nested.opt(index) as? JSONObject)?.let(output::add)
                }
            }
        }
        when (payload) {
            is JSONArray -> for (index in 0 until payload.length()) add(payload.opt(index))
            is JSONObject -> {
                val matches = payload.optJSONArray("m")
                if (matches != null) {
                    for (index in 0 until matches.length()) add(matches.opt(index))
                } else {
                    add(payload)
                }
            }
        }
        return output
    }

    private fun teamFlagUrl(teamKey: String): String? {
        return teamKey.takeIf { TEAM_KEY_PATTERN.matches(it) }
            ?.let { "https://cricketvectors.akamaized.net/Teams/" + it + ".png" }
    }

    private fun abbreviation(name: String, fallback: String): String {
        if (name == fallback || name.isBlank()) return fallback.take(3).uppercase(Locale.US)
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(3).joinToString("") { it.first().toString() }
            .uppercase(Locale.US)
            .ifBlank { name.take(3).uppercase(Locale.US) }
    }

    private fun formatScore(score: String?, overs: String?): String? {
        val raw = score?.trim()?.takeIf { it.isNotBlank() && it != "0/0" } ?: return null
        val openParen = raw.indexOf('(')
        val scorePart = if (openParen >= 0) raw.substring(0, openParen).trim() else raw
        if (scorePart == "0/0") return null
        val embeddedOvers = if (openParen >= 0) {
            raw.substring(openParen + 1).trim().trimEnd(')', ' ').takeIf { it.isNotBlank() }
        } else {
            null
        }
        val overText = embeddedOvers ?: overs?.trim()?.takeIf { it.isNotBlank() }
        return if (overText != null) "$scorePart ($overText)" else scorePart
    }

    private fun firstText(json: JSONObject?, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> text(json, key) }
    }

    private fun text(json: JSONObject?, key: String): String? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return json.opt(key)?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun firstLong(json: JSONObject, vararg keys: String): Long {
        return keys.firstNotNullOfOrNull { key ->
            if (!json.has(key) || json.isNull(key)) {
                null
            } else {
                when (val raw = json.opt(key)) {
                    is Number -> raw.toLong()
                    is String -> raw.toLongOrNull()
                    else -> null
                }
            }
        } ?: 0L
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
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

    private data class MatchCandidate(val id: String, val row: JSONObject)
    private data class FetchedFixture(val fixture: MatchFixture?, val teamNames: Map<String, String>)

    companion object {
        private const val HOME_MATCHES_URL = "https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE"
        private const val FILTERED_MATCHES_URL = "https://crickapi.com/home/getFilteredMatches"
        private const val LIVE_MATCHES_URL = "https://api.goscorer.com/api/v3/getLiveMatches"
        private const val MATCH_DETAIL_URL = "https://api.goscorer.com/api/v3/getSV3"
        private const val SCHEDULE_PAGE_URL = "https://crex.com/schedule"
        private const val TEAM_NAME_CACHE_MS = 6 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TEAM_KEY_PATTERN = Regex("[A-Za-z0-9]+")
        private val INTERNATIONAL_FORMAT_PATTERN = Pattern.compile(
            "\\b(?:T20I|WT20I|ODI|WODI|TEST)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val FINAL_RESULT_PATTERN = Regex(
            "\\b(?:won|drawn?|no result|abandon(?:ed)?|tied|super\\s*over|completed|finished)\\b",
            RegexOption.IGNORE_CASE
        )
        private val IMAGE_TAG_PATTERN = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE)
        private val TEAM_IMAGE_PATTERN = Pattern.compile("/Teams/([^/\"'?]+)\\.png", Pattern.CASE_INSENSITIVE)
        private val TEAM_ALT_PATTERN = Pattern.compile("\\balt=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }
}


/** Converts the cricket provider's match status code into the app's filter bucket. */
internal fun cricketStatusFromCode(
    code: Int,
    fallback: MatchStatus? = null,
    startTimeEpochMs: Long? = null,
    hasFinalResult: Boolean = false,
    nowMs: Long = System.currentTimeMillis()
): MatchStatus? {
    if (hasFinalResult) return MatchStatus.FINISHED
    return when {
        code == 0 -> when {
            startTimeEpochMs == null -> fallback ?: MatchStatus.UPCOMING
            startTimeEpochMs > nowMs -> MatchStatus.UPCOMING
            fallback == MatchStatus.FINISHED -> MatchStatus.FINISHED
            else -> null
        }
        code == 1 -> if (startTimeEpochMs != null && startTimeEpochMs > nowMs) {
            MatchStatus.UPCOMING
        } else {
            MatchStatus.LIVE
        }
        code >= 2 -> MatchStatus.FINISHED
        fallback == MatchStatus.FINISHED -> MatchStatus.FINISHED
        startTimeEpochMs == null -> fallback
        startTimeEpochMs > nowMs -> fallback ?: MatchStatus.UPCOMING
        else -> null
    }
}
