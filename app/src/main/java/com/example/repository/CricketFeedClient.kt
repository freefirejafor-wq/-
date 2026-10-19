package com.example.repository

import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
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
                withContext(Dispatchers.IO) {
                    fetchMatchList(type = 1, status = MatchStatus.UPCOMING, teamNames = teamNames)
                }
            }
            val finishedResult = capture {
                withContext(Dispatchers.IO) {
                    fetchMatchList(type = 0, status = MatchStatus.FINISHED, teamNames = teamNames)
                }
            }

            if (liveResult.isFailure && upcomingResult.isFailure && finishedResult.isFailure) {
                throw IOException("Cricket match feeds are temporarily unavailable.")
            }

            val byId = linkedMapOf<String, MatchFixture>()
            finishedResult.getOrNull().orEmpty().forEach { byId[it.id] = it }
            upcomingResult.getOrNull().orEmpty().forEach { byId[it.id] = it }
            liveResult.getOrNull().orEmpty().forEach { byId[it.id] = it }

            val sorted = byId.values.sortedWith(
                compareBy<MatchFixture> {
                    when (it.status) {
                        MatchStatus.LIVE -> 0
                        MatchStatus.UPCOMING -> 1
                        MatchStatus.FINISHED -> 2
                        MatchStatus.ALL -> 3
                    }
                }.thenBy {
                    if (it.status == MatchStatus.FINISHED) {
                        -it.startTimeEpochMs
                    } else {
                        it.startTimeEpochMs
                    }
                }
            )
            Result.success(sorted)
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
        val ids = liveRoot.keys().asSequence().toList()
        val semaphore = Semaphore(4)

        val results = coroutineScope {
            ids.map { id ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val detailUrl = "$MATCH_DETAIL_URL?key=${URLEncoder.encode(id, "UTF-8")}"
                            val detail = requestJson(detailUrl) as? JSONObject ?: return@withPermit null
                            val match = liveRoot.optJSONObject(id) ?: JSONObject()
                            val names = parseSpeechNames(detail)
                            val fixture = if (detail.optInt("ms", 0) > 0) {
                                parseLiveFixture(id, match, detail, teamNames + names)
                            } else {
                                null
                            }
                            LiveFixtureResult(fixture, names)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        results.forEach { teamNames.putAll(it.names) }
        return results.mapNotNull { it.fixture }
    }

    private fun fetchMatchList(
        type: Int,
        status: MatchStatus,
        teamNames: MutableMap<String, String>
    ): List<MatchFixture> {
        val primaryPayload = runCatching {
            requestJson(
                HOME_MATCHES_URL,
                JSONObject().put("type", type)
            )
        }.getOrNull()
        val primaryMatches = primaryPayload
            ?.let(::flattenMatchObjects)
            .orEmpty()
            .mapNotNull { parseFixture(it, status, teamNames) }

        if (primaryMatches.isNotEmpty()) return primaryMatches

        // The filtered endpoint is a fallback for the same Upcoming/Finished feeds.
        val fallbackPayload = requestJson(
            FILTERED_MATCHES_URL,
            JSONObject().put("flag", 1).put("type", type)
        )
        return flattenMatchObjects(fallbackPayload)
            .mapNotNull { parseFixture(it, status, teamNames) }
    }

    private fun parseFixture(
        json: JSONObject,
        status: MatchStatus,
        teamNames: MutableMap<String, String>
    ): MatchFixture? {
        rememberGlobalTeamName(json, teamNames)

        val matchKey = firstText(json, "mf", "mfkey", "nf", "matchFkey", "id") ?: return null
        val team1Key = firstText(json, "t1f", "team1fkey").orEmpty()
        val team2Key = firstText(json, "t2f", "team2fkey").orEmpty()
        val team1 = firstText(json, "team1", "team1Name")
            ?: teamNames[team1Key]
            ?: team1Key.ifBlank { "Team 1" }
        val team2 = firstText(json, "team2", "team2Name")
            ?: teamNames[team2Key]
            ?: team2Key.ifBlank { "Team 2" }
        val format = firstText(json, "fo", "formats", "match_type")
        val matchNumber = firstText(json, "mn", "match_number", "matchNums")
        val league = listOfNotNull(
            "CRICKET",
            format?.takeIf { it.isNotBlank() },
            matchNumber?.takeIf { it.isNotBlank() }?.let { "Match $it" }
        ).joinToString(" | ")

        return MatchFixture(
            id = "cricket_$matchKey",
            title = "$team1 vs $team2",
            sport = SportType.CRICKET,
            league = league,
            team1Name = team1,
            team1LogoUrl = teamFlagUrl(team1Key),
            team1Abbr = abbreviation(team1, team1Key),
            team2Name = team2,
            team2LogoUrl = teamFlagUrl(team2Key),
            team2Abbr = abbreviation(team2, team2Key),
            startTimeEpochMs = firstLong(json, "t", "mt", "startTimeEpochMs")
                .takeIf { it > 0L } ?: System.currentTimeMillis(),
            status = status,
            team1Score = formatScore(
                firstText(json, "score1", "s1", "team1Score1"),
                firstText(json, "overs1", "o1", "team1Over1")
            ),
            team2Score = formatScore(
                firstText(json, "score2", "s2", "team2Score1"),
                firstText(json, "overs2", "o2", "team2Over1")
            ),
            streams = emptyList(),
            isDemo = false
        )
    }

    private fun parseLiveFixture(
        id: String,
        liveItem: JSONObject,
        detail: JSONObject,
        teamNames: Map<String, String>
    ): MatchFixture {
        val teamKeys = teamKeysFrom(detail, liveItem)
        val team1Key = teamKeys.first
        val team2Key = teamKeys.second
        val team1 = teamNames[team1Key] ?: team1Key.ifBlank { "Team 1" }
        val team2 = teamNames[team2Key] ?: team2Key.ifBlank { "Team 2" }
        val format = firstText(detail, "fo") ?: "Cricket"
        val matchNumber = firstText(detail, "mn")
        val league = listOfNotNull(
            "CRICKET",
            format,
            matchNumber?.takeIf { it.isNotBlank() }?.let { "Match $it" }
        ).joinToString(" | ")

        return MatchFixture(
            id = "cricket_$id",
            title = "$team1 vs $team2",
            sport = SportType.CRICKET,
            league = league,
            team1Name = team1,
            team1LogoUrl = teamFlagUrl(team1Key),
            team1Abbr = abbreviation(team1, team1Key),
            team2Name = team2,
            team2LogoUrl = teamFlagUrl(team2Key),
            team2Abbr = abbreviation(team2, team2Key),
            startTimeEpochMs = firstLong(detail, "mt", "ed")
                .takeIf { it > 0L }
                ?: firstLong(liveItem, "ti", "t").takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            status = MatchStatus.LIVE,
            liveCounter = null,
            team1Score = formatScore(firstText(detail, "j"), null),
            team2Score = formatScore(firstText(detail, "k"), null),
            streams = emptyList(),
            isDemo = false
        )
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

    private fun requestJson(url: String, body: JSONObject? = null): Any {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (body == null) {
                    get()
                } else {
                    post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Cricket API returned HTTP ${response.code}.")
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
                throw IOException("Cricket schedule returned HTTP ${response.code}.")
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

    private fun parseSpeechNames(detail: JSONObject): Map<String, String> {
        val speechNames = detail.optJSONObject("speech_names") ?: return emptyMap()
        val names = mutableMapOf<String, String>()
        val keys = speechNames.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            value(speechNames, key)?.takeIf { it.isNotBlank() }?.let { names[key] = it }
        }
        return names
    }

    private fun teamKeysFrom(detail: JSONObject, liveItem: JSONObject): Pair<String, String> {
        val fromDetail = firstText(detail, "a")
            ?.split(".")
            ?.takeIf { it.size >= 2 && isTeamKey(it[0]) && isTeamKey(it[1]) }
        val first = fromDetail?.get(0) ?: firstText(liveItem, "b", "t1f").orEmpty()
        val second = fromDetail?.get(1) ?: firstText(liveItem, "c", "t2f").orEmpty()
        return first to second
    }

    private fun rememberGlobalTeamName(json: JSONObject, teamNames: MutableMap<String, String>) {
        val global = json.optJSONObject("global_num") ?: return
        val key = firstText(global, "fri")?.removePrefix("^")?.trim().orEmpty()
        val name = firstText(global, "fr")?.trim().orEmpty()
        if (key.isNotBlank() && name.isNotBlank()) teamNames[key] = name
    }

    private fun teamFlagUrl(teamKey: String): String? {
        return teamKey.takeIf(::isTeamKey)?.let { "https://cricketvectors.akamaized.net/Teams/$it.png" }
    }

    private fun isTeamKey(value: String): Boolean = TEAM_KEY_PATTERN.matches(value)

    private fun abbreviation(name: String, fallback: String): String {
        if (name == fallback || name.isBlank()) return fallback.take(3).uppercase()
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val initials = words.take(3).joinToString("") { it.first().toString() }.uppercase()
        return initials.ifBlank { name.take(3).uppercase() }
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

    private fun firstText(json: JSONObject, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> value(json, key) }
    }

    private fun value(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
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

    private data class LiveFixtureResult(
        val fixture: MatchFixture?,
        val names: Map<String, String>
    )

    companion object {
        private const val HOME_MATCHES_URL = "https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE"
        private const val FILTERED_MATCHES_URL = "https://crickapi.com/home/getFilteredMatches"
        private const val LIVE_MATCHES_URL = "https://api.goscorer.com/api/v3/getLiveMatches"
        private const val MATCH_DETAIL_URL = "https://api.goscorer.com/api/v3/getSV3"
        private const val SCHEDULE_PAGE_URL = "https://crex.com/schedule"
        private const val TEAM_NAME_CACHE_MS = 6 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TEAM_KEY_PATTERN = Regex("[A-Za-z0-9]+")
        private val IMAGE_TAG_PATTERN = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE)
        private val TEAM_IMAGE_PATTERN = Pattern.compile("/Teams/([^/\"'?]+)\\.png", Pattern.CASE_INSENSITIVE)
        private val TEAM_ALT_PATTERN = Pattern.compile("\\balt=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }
}
