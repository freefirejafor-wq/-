package com.example.repository

import com.example.model.M3uChannel
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamFormat
import com.example.model.StreamSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.TimeZone

interface SportsRepository {
    val fixturesFlow: Flow<List<MatchFixture>>
    val favoritesFlow: Flow<Set<String>>
    val m3uChannelsFlow: Flow<List<M3uChannel>>

    suspend fun refreshFixtures(): Result<Unit>
    suspend fun toggleFavorite(fixtureId: String)
    suspend fun importM3uPlaylist(source: String, isUrl: Boolean): Result<Int>
    suspend fun addCustomStream(title: String, url: String, format: StreamFormat): Result<Unit>
    fun getApiConfigDocumentation(): ApiDocumentation
}

data class ApiDocumentation(
    val title: String,
    val description: String,
    val endpointSample: String,
    val jsonSchemaExample: String,
    val backendRecommendation: String
)

class SportsRepositoryImpl : SportsRepository {

    private val _fixtures = MutableStateFlow<List<MatchFixture>>(emptyList())
    override val fixturesFlow: Flow<List<MatchFixture>> = _fixtures.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(setOf("fixture_hockey_1", "fixture_football_1"))
    override val favoritesFlow: Flow<Set<String>> = _favorites.asStateFlow()

    private val _m3uChannels = MutableStateFlow<List<M3uChannel>>(emptyList())
    override val m3uChannelsFlow: Flow<List<M3uChannel>> = _m3uChannels.asStateFlow()

    init {
        loadDemoFixtures()
        loadSampleChannels()
    }

    private fun loadDemoFixtures() {
        val dhakaTz = TimeZone.getTimeZone("Asia/Dhaka")
        val cal = Calendar.getInstance(dhakaTz)

        // Authorized public streaming test assets (Media3 HLS & DASH)
        val hlsTestStream1 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val hlsLiveTestStream2 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        val dashTestStream3 = "https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd"

        val demoList = listOf(
            MatchFixture(
                id = "fixture_hockey_1",
                title = "Flames vs Hurricanes",
                sport = SportType.HOCKEY,
                league = "HOCKEY | NHL",
                team1Name = "Flames",
                team1Abbr = "CGY",
                team2Name = "Hurricanes",
                team2Abbr = "CAR",
                startTimeEpochMs = System.currentTimeMillis() - 3600000,
                status = MatchStatus.LIVE,
                liveCounter = "03:26:41",
                team1Score = "2",
                team2Score = "1",
                streams = listOf(
                    StreamSource(
                        id = "stream_1",
                        name = "PREMIER SPORTS FHD",
                        qualityLabel = "FHD",
                        streamUrl = hlsTestStream1,
                        format = StreamFormat.HLS,
                        bitrateInfo = "1080p 60fps • 5.2 Mbps",
                        isDemo = true
                    ),
                    StreamSource(
                        id = "stream_2",
                        name = "HD SERVER",
                        qualityLabel = "HD",
                        streamUrl = hlsLiveTestStream2,
                        format = StreamFormat.HLS,
                        bitrateInfo = "720p 30fps • 2.8 Mbps",
                        isDemo = true
                    ),
                    StreamSource(
                        id = "stream_3",
                        name = "DASH HIGH EFFICIENCY",
                        qualityLabel = "4K",
                        streamUrl = dashTestStream3,
                        format = StreamFormat.DASH,
                        bitrateInfo = "MPEG-DASH Adaptive",
                        isDemo = true
                    )
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_cricket_1",
                title = "OMA vs NEP",
                sport = SportType.CRICKET,
                league = "CRICKET | ICC CWC LEAGUE 2",
                team1Name = "OMA",
                team1Abbr = "OMA",
                team2Name = "NEP",
                team2Abbr = "NEP",
                startTimeEpochMs = System.currentTimeMillis() + (2 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 2 hours",
                streams = listOf(
                    StreamSource("cricket_s1", "SERVER 1 FHD", "FHD", hlsLiveTestStream2, StreamFormat.HLS),
                    StreamSource("cricket_s2", "SERVER 2 SD", "SD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_cricket_2",
                title = "SA vs AUS",
                sport = SportType.CRICKET,
                league = "CRICKET | ICC WORLD TEST CHAMPIONSHIP",
                team1Name = "SA",
                team1Abbr = "SA",
                team2Name = "AUS",
                team2Abbr = "AUS",
                startTimeEpochMs = System.currentTimeMillis() + (4 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 4 hours",
                streams = listOf(
                    StreamSource("sa_s1", "WILLOW HD", "HD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_cricket_3",
                title = "AFG vs ZIM",
                sport = SportType.CRICKET,
                league = "CRICKET | ODI TRI-SERIES IN UNITED ARAB EMIRATES",
                team1Name = "AFG",
                team1Abbr = "AFG",
                team2Name = "ZIM",
                team2Abbr = "ZIM",
                startTimeEpochMs = System.currentTimeMillis() + (8 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 8 hours",
                streams = listOf(
                    StreamSource("afg_s1", "SPORTS 1 HD", "HD", hlsTestStream1, StreamFormat.HLS),
                    StreamSource("afg_s2", "SPORTS 2 FHD", "FHD", hlsLiveTestStream2, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_football_1",
                title = "Punjab vs Mumbai City",
                sport = SportType.FOOTBALL,
                league = "FOOTBALL | INDIAN SUPER LEAGUE",
                team1Name = "Punjab",
                team1Abbr = "PFC",
                team2Name = "Mumbai City",
                team2Abbr = "MCFC",
                startTimeEpochMs = System.currentTimeMillis() + (10 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 10 hours",
                streams = listOf(
                    StreamSource("isl_s1", "JIO SPORTS HD", "HD", hlsLiveTestStream2, StreamFormat.HLS),
                    StreamSource("isl_s2", "STAR SPORTS 1", "FHD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_football_2",
                title = "Sabah U19 vs Dortmund U19",
                sport = SportType.FOOTBALL,
                league = "FOOTBALL | UEFA YOUTH LEAGUE",
                team1Name = "Sabah U19",
                team1Abbr = "SAB",
                team2Name = "Dortmund U19",
                team2Abbr = "BVB",
                startTimeEpochMs = System.currentTimeMillis() + (24 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 1 days",
                streams = listOf(
                    StreamSource("uefa_s1", "UEFA TV STREAM", "HD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_football_3",
                title = "Abha vs Al-Kholood",
                sport = SportType.FOOTBALL,
                league = "FOOTBALL | SAUDI PRO LEAGUE",
                team1Name = "Abha",
                team1Abbr = "ABH",
                team2Name = "Al-Kholood",
                team2Abbr = "KHL",
                startTimeEpochMs = System.currentTimeMillis() + (11 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 11 hours",
                streams = listOf(
                    StreamSource("spl_s1", "SSC 1 HD", "HD", hlsLiveTestStream2, StreamFormat.HLS),
                    StreamSource("spl_s2", "SSC 2 FHD", "FHD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_baseball_1",
                title = "Rays vs Guardians",
                sport = SportType.BASEBALL,
                league = "BASEBALL | MLB",
                team1Name = "Rays",
                team1Abbr = "TB",
                team2Name = "Guardians",
                team2Abbr = "CLE",
                startTimeEpochMs = System.currentTimeMillis() + (20 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 20 hours",
                streams = listOf(
                    StreamSource("mlb_s1", "BALLY SPORTS HD", "HD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_motorsport_1",
                title = "MotoGP Grand Prix Qualifying",
                sport = SportType.MOTORSPORT,
                league = "MOTORSPORT | MOTOGP",
                team1Name = "Ducati Lenovo",
                team1Abbr = "DUC",
                team2Name = "Red Bull KTM",
                team2Abbr = "KTM",
                startTimeEpochMs = System.currentTimeMillis() + (72 * 3600000),
                status = MatchStatus.UPCOMING,
                startsInText = "Starts in 3 days",
                streams = listOf(
                    StreamSource("mgp_s1", "MOTOGP LIVE FEED", "FHD", hlsLiveTestStream2, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_finished_1",
                title = "Arsenal vs Manchester City",
                sport = SportType.FOOTBALL,
                league = "FOOTBALL | PREMIER LEAGUE",
                team1Name = "Arsenal",
                team1Abbr = "ARS",
                team2Name = "Man City",
                team2Abbr = "MCI",
                startTimeEpochMs = System.currentTimeMillis() - (86400000),
                status = MatchStatus.FINISHED,
                team1Score = "2",
                team2Score = "1",
                streams = listOf(
                    StreamSource("replay_1", "MATCH REPLAY HD", "HD", hlsTestStream1, StreamFormat.HLS)
                ),
                isDemo = true
            ),
            MatchFixture(
                id = "fixture_finished_2",
                title = "Real Madrid vs Barcelona",
                sport = SportType.FOOTBALL,
                league = "FOOTBALL | LA LIGA",
                team1Name = "Real Madrid",
                team1Abbr = "RMA",
                team2Name = "Barcelona",
                team2Abbr = "BAR",
                startTimeEpochMs = System.currentTimeMillis() - (172800000),
                status = MatchStatus.FINISHED,
                team1Score = "3",
                team2Score = "2",
                streams = listOf(
                    StreamSource("replay_2", "EL CLASICO REPLAY", "FHD", hlsLiveTestStream2, StreamFormat.HLS)
                ),
                isDemo = true
            )
        )

        _fixtures.value = demoList
    }

    private fun loadSampleChannels() {
        val channels = listOf(
            M3uChannel(
                id = "ch_1",
                name = "Premier Sports 1 HD",
                group = "Football",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                format = StreamFormat.HLS
            ),
            M3uChannel(
                id = "ch_2",
                name = "Live Sports 24/7",
                group = "Sports",
                streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                format = StreamFormat.HLS
            ),
            M3uChannel(
                id = "ch_3",
                name = "Dash Sports Ultra",
                group = "Sports",
                streamUrl = "https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd",
                format = StreamFormat.DASH
            ),
            M3uChannel(
                id = "ch_4",
                name = "International Cricket Live",
                group = "Cricket",
                streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                format = StreamFormat.HLS
            ),
            M3uChannel(
                id = "ch_5",
                name = "Euro Football Arena",
                group = "Football",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                format = StreamFormat.HLS
            )
        )
        _m3uChannels.value = channels
    }

    override suspend fun refreshFixtures(): Result<Unit> {
        // Simulates fetching latest fixtures or pulling from external Sports API
        return Result.success(Unit)
    }

    override suspend fun toggleFavorite(fixtureId: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(fixtureId)) {
            current.remove(fixtureId)
        } else {
            current.add(fixtureId)
        }
        _favorites.value = current
    }

    override suspend fun importM3uPlaylist(source: String, isUrl: Boolean): Result<Int> {
        return try {
            val content = if (isUrl) {
                // If it's a URL, fetch content using OkHttp
                fetchUrlContent(source)
            } else {
                source
            }
            val parsed = com.example.util.M3uParser.parse(content)
            if (parsed.isEmpty()) {
                Result.failure(Exception("No valid stream channels found in playlist."))
            } else {
                _m3uChannels.value = _m3uChannels.value + parsed
                Result.success(parsed.size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchUrlContent(url: String): String {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    override suspend fun addCustomStream(title: String, url: String, format: StreamFormat): Result<Unit> {
        val newChannel = M3uChannel(
            id = java.util.UUID.randomUUID().toString(),
            name = title.ifBlank { "Custom Stream" },
            group = "Custom Streams",
            streamUrl = url,
            format = format
        )
        _m3uChannels.value = listOf(newChannel) + _m3uChannels.value
        return Result.success(Unit)
    }

    override fun getApiConfigDocumentation(): ApiDocumentation {
        return ApiDocumentation(
            title = "Football Live TV - External Sports API Integration",
            description = "The app is architected with a clean Repository pattern ready to connect to any REST or WebSocket sports live telemetry & stream provider.",
            endpointSample = "GET /api/v1/fixtures?timezone=Asia/Dhaka&sport=all",
            jsonSchemaExample = """
                {
                  "fixtures": [
                    {
                      "id": "match_101",
                      "title": "Arsenal vs Chelsea",
                      "sport": "FOOTBALL",
                      "league": "FOOTBALL | PREMIER LEAGUE",
                      "team1": { "name": "Arsenal", "abbr": "ARS", "logo": "https://..." },
                      "team2": { "name": "Chelsea", "abbr": "CHE", "logo": "https://..." },
                      "startTimeEpochMs": 1792400000000,
                      "status": "LIVE",
                      "liveCounter": "45+2'",
                      "scores": { "team1": 1, "team2": 0 },
                      "streams": [
                        {
                          "id": "str_1",
                          "name": "PREMIER SPORTS FHD",
                          "quality": "FHD",
                          "url": "https://your-cdn.com/live/hls/arsenal.m3u8",
                          "format": "HLS",
                          "bitrate": "1080p 60fps"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            backendRecommendation = "Security Architecture Note: Live stream tokens, private CDN signatures, and admin rights must always reside on a secure cloud backend (such as a protected API gateway) rather than inside local client code."
        )
    }
}
