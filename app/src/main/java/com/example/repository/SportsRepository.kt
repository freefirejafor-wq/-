package com.example.repository

import com.example.model.M3uChannel
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val cricketFeedClient = CricketFeedClient()
    private val footballFeedClient = FootballFeedClient()

    private val _fixtures = MutableStateFlow<List<MatchFixture>>(emptyList())
    override val fixturesFlow: Flow<List<MatchFixture>> = _fixtures.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    override val favoritesFlow: Flow<Set<String>> = _favorites.asStateFlow()

    private val _m3uChannels = MutableStateFlow<List<M3uChannel>>(emptyList())
    override val m3uChannelsFlow: Flow<List<M3uChannel>> = _m3uChannels.asStateFlow()

    init {
        loadSampleChannels()
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

    override suspend fun refreshFixtures(): Result<Unit> = coroutineScope {
        val cricketRequest = async { cricketFeedClient.fetchFixtures() }
        val footballRequest = async { footballFeedClient.fetchFixtures() }
        val cricketResult = cricketRequest.await()
        val footballResult = footballRequest.await()

        val existing = _fixtures.value
        val cricketFixtures = cricketResult.getOrNull()
            ?: existing.filter { it.sport == SportType.CRICKET }
        val footballFixtures = footballResult.getOrNull()
            ?: existing.filter { it.sport == SportType.FOOTBALL }
        _fixtures.value = cricketFixtures + footballFixtures

        if (cricketResult.isSuccess && footballResult.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("One or more sports feeds are temporarily unavailable."))
        }
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
