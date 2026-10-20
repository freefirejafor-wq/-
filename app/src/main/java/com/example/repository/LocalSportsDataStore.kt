package com.example.repository

import android.content.Context
import android.util.Base64
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.M3uChannel
import com.example.model.SportType
import com.example.model.StreamFormat
import com.example.model.StreamSource
import com.example.util.M3uParser
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** All administrator edits are stored on this installation only. */
class LocalSportsDataStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val channelsAdapter: JsonAdapter<List<M3uChannel>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, M3uChannel::class.java)
    )
    private val overridesAdapter: JsonAdapter<List<MatchAdminOverride>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MatchAdminOverride::class.java)
    )

    fun loadAdminChannels(): List<M3uChannel> {
        val raw = preferences.getString(KEY_CHANNELS, null) ?: return defaultSampleChannels()
        return runCatching { channelsAdapter.fromJson(raw) }.getOrNull() ?: defaultSampleChannels()
    }

    fun saveChannels(channels: List<M3uChannel>) {
        preferences.edit().putString(KEY_CHANNELS, channelsAdapter.toJson(channels)).apply()
    }

    fun saveChannel(channel: M3uChannel) {
        val channels = loadAdminChannels().toMutableList()
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index >= 0) channels[index] = channel else channels.add(channel)
        saveChannels(channels)
    }

    fun saveChannelOrder(channels: List<M3uChannel>) {
        saveChannels(channels.mapIndexed { index, channel -> channel.copy(displayOrder = index) })
    }

    fun deleteChannel(channelId: String) {
        saveChannels(loadAdminChannels().filterNot { it.id == channelId })
    }

    fun loadMatchOverrides(): Map<String, MatchAdminOverride> {
        val raw = preferences.getString(KEY_MATCH_OVERRIDES, null) ?: return emptyMap()
        return runCatching { overridesAdapter.fromJson(raw).orEmpty().associateBy { it.id } }
            .getOrDefault(emptyMap())
    }

    fun saveMatchOverride(fixture: MatchFixture, isManual: Boolean) {
        val updated = loadMatchOverrides().toMutableMap()
        updated[fixture.id] = MatchAdminOverride(
            id = fixture.id,
            title = fixture.title,
            sport = fixture.sport,
            league = fixture.league,
            team1Name = fixture.team1Name,
            team1LogoUrl = fixture.team1LogoUrl,
            team1Abbr = fixture.team1Abbr,
            team2Name = fixture.team2Name,
            team2LogoUrl = fixture.team2LogoUrl,
            team2Abbr = fixture.team2Abbr,
            startTimeEpochMs = fixture.startTimeEpochMs,
            status = fixture.status,
            liveCounter = fixture.liveCounter,
            team1Score = fixture.team1Score,
            team2Score = fixture.team2Score,
            streams = fixture.streams,
            isManual = isManual
        )
        saveOverrides(updated.values.toList())
    }

    fun deleteMatchOverride(matchId: String) {
        saveOverrides(loadMatchOverrides().values.filterNot { it.id == matchId })
    }

    private fun saveOverrides(overrides: List<MatchAdminOverride>) {
        preferences.edit().putString(KEY_MATCH_OVERRIDES, overridesAdapter.toJson(overrides)).apply()
    }

    suspend fun importM3uPlaylist(source: String, isUrl: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = if (isUrl) {
                val request = Request.Builder().url(source.trim()).build()
                OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
                    .newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Playlist request returned HTTP " + response.code + ".")
                        response.body?.string().orEmpty()
                    }
            } else {
                source
            }
            val parsed = M3uParser.parse(content)
            if (parsed.isEmpty()) {
                Result.failure(IOException("No valid channels were found in the M3U playlist."))
            } else {
                saveChannels(loadAdminChannels() + parsed)
                Result.success(parsed.size)
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun hasLocalPassword(): Boolean = preferences.contains(KEY_PASSWORD_HASH) && preferences.contains(KEY_PASSWORD_SALT)

    suspend fun setLocalPassword(password: String) = withContext(Dispatchers.Default) {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Choose a password with at least 8 characters." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derivePasswordHash(password, salt)
        preferences.edit()
            .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    suspend fun verifyLocalPassword(password: String): Boolean = withContext(Dispatchers.Default) {
        val saltValue = preferences.getString(KEY_PASSWORD_SALT, null) ?: return@withContext false
        val hashValue = preferences.getString(KEY_PASSWORD_HASH, null) ?: return@withContext false
        runCatching {
            val salt = Base64.decode(saltValue, Base64.NO_WRAP)
            val expected = Base64.decode(hashValue, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derivePasswordHash(password, salt))
        }.getOrDefault(false)
    }

    private fun derivePasswordHash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "sports_admin_local"
        const val KEY_CHANNELS = "channels_json"
        const val KEY_MATCH_OVERRIDES = "match_overrides_json"
        const val KEY_PASSWORD_HASH = "local_password_hash"
        const val KEY_PASSWORD_SALT = "local_password_salt"
        const val MIN_PASSWORD_LENGTH = 8
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
        const val PASSWORD_ITERATIONS = 150_000
    }
}

data class MatchAdminOverride(
    val id: String,
    val title: String,
    val sport: SportType,
    val league: String,
    val team1Name: String,
    val team1LogoUrl: String?,
    val team1Abbr: String,
    val team2Name: String,
    val team2LogoUrl: String?,
    val team2Abbr: String,
    val startTimeEpochMs: Long,
    val status: MatchStatus,
    val liveCounter: String?,
    val team1Score: String?,
    val team2Score: String?,
    val streams: List<StreamSource>,
    val isManual: Boolean
) {
    fun applyTo(base: MatchFixture) = base.copy(
        title = title,
        sport = sport,
        league = league,
        team1Name = team1Name,
        team1LogoUrl = team1LogoUrl,
        team1Abbr = team1Abbr,
        team2Name = team2Name,
        team2LogoUrl = team2LogoUrl,
        team2Abbr = team2Abbr,
        startTimeEpochMs = startTimeEpochMs,
        status = status,
        liveCounter = liveCounter,
        startsInText = null,
        team1Score = team1Score,
        team2Score = team2Score,
        streams = streams,
        isDemo = false
    )

    fun toFixture(): MatchFixture? {
        if (!isManual || team1Name.isBlank() || team2Name.isBlank() || startTimeEpochMs <= 0L) return null
        return MatchFixture(
            id = id,
            title = title.ifBlank { team1Name + " vs " + team2Name },
            sport = sport,
            league = league.ifBlank { sport.displayName },
            team1Name = team1Name,
            team1LogoUrl = team1LogoUrl,
            team1Abbr = team1Abbr.ifBlank { team1Name.take(3).uppercase() },
            team2Name = team2Name,
            team2LogoUrl = team2LogoUrl,
            team2Abbr = team2Abbr.ifBlank { team2Name.take(3).uppercase() },
            startTimeEpochMs = startTimeEpochMs,
            status = status,
            liveCounter = liveCounter,
            team1Score = team1Score,
            team2Score = team2Score,
            streams = streams,
            isDemo = false
        )
    }
}

fun defaultSampleChannels(): List<M3uChannel> = listOf(
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

suspend fun checkStreamAvailability(url: String): Result<String> = withContext(Dispatchers.IO) {
    try {
        require(url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)) {
            "Use an http:// or https:// stream URL."
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Result.success("Reachable • HTTP " + response.code)
            } else {
                Result.failure(IOException("Server returned HTTP " + response.code + "."))
            }
        }
    } catch (error: Exception) {
        Result.failure(IOException("Could not reach this stream (" + (error::class.simpleName ?: "network error") + ")."))
    }
}
