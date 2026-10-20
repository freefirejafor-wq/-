package com.example.repository

import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.M3uChannel
import com.example.model.SportType
import com.example.model.StreamFormat
import com.example.model.StreamSource
import com.example.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
            title = title.ifBlank { "$team1Name vs $team2Name" },
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

/**
 * Firebase-backed data access for public app data and the authenticated admin screen.
 * Firestore rules, not this client-side gate, are the authority for every write.
 */
class FirebaseSportsDataStore {
    private val firebaseApp: FirebaseApp? = runCatching { FirebaseApp.getInstance() }.getOrNull()
    private val auth: FirebaseAuth? = firebaseApp?.let { app ->
        runCatching { FirebaseAuth.getInstance(app) }.getOrNull()
    }
    private val firestore: FirebaseFirestore? = firebaseApp?.let { app ->
        runCatching {
            val databaseId = BuildConfig.FIRESTORE_DATABASE_ID.trim()
            if (databaseId.isBlank() || databaseId == "(default)") {
                FirebaseFirestore.getInstance(app)
            } else {
                FirebaseFirestore.getInstance(app, databaseId)
            }
        }.getOrNull()
    }

    val isConfigured: Boolean
        get() = auth != null && firestore != null

    val currentUserEmail: String?
        get() = auth?.currentUser?.email

    suspend fun signInAsAdmin(email: String, password: String) {
        val firebaseAuth = checkNotNull(auth) { "Firebase Auth is not configured in this APK." }
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        val user = firebaseAuth.signInWithEmailAndPassword(email, password)
            .awaitResult()
            .user
            ?: throw IOException("Sign-in did not return a user.")
        val adminRecord = database.collection("admins").document(user.uid).get().awaitResult()
        if (!adminRecord.exists()) {
            firebaseAuth.signOut()
            throw SecurityException("This account is not authorized as an app admin.")
        }
    }

    suspend fun hasAdminAccess(): Boolean {
        val user = auth?.currentUser ?: return false
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        return database.collection("admins").document(user.uid).get().awaitResult().exists()
    }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun loadAdminChannels(): List<M3uChannel> {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        return database.collection("channels")
            .orderBy("sortOrder")
            .get()
            .awaitResult()
            .documents
            .mapNotNull { document ->
                FirestoreChannelMapper.fromDocument(
                    document.id,
                    document.data.orEmpty(),
                    includeInactive = true
                )?.channel
            }
    }

    suspend fun saveChannel(channel: M3uChannel) {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        database.collection("channels")
            .document(channel.id)
            .set(channel.toFirestoreMap(), SetOptions.merge())
            .awaitResult()
    }

    suspend fun saveChannelOrder(channels: List<M3uChannel>) {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        val batch = database.batch()
        channels.forEachIndexed { index, channel ->
            batch.set(
                database.collection("channels").document(channel.id),
                channel.copy(displayOrder = index).toFirestoreMap(),
                SetOptions.merge()
            )
        }
        batch.commit().awaitResult()
    }

    suspend fun deleteChannel(channelId: String) {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        database.collection("channels").document(channelId).delete().awaitResult()
    }

    suspend fun saveMatchOverride(fixture: MatchFixture, isManual: Boolean) {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        val record = mapOf(
            "title" to fixture.title,
            "sport" to fixture.sport.name,
            "league" to fixture.league,
            "team1Name" to fixture.team1Name,
            "team1LogoUrl" to fixture.team1LogoUrl,
            "team1Abbr" to fixture.team1Abbr,
            "team2Name" to fixture.team2Name,
            "team2LogoUrl" to fixture.team2LogoUrl,
            "team2Abbr" to fixture.team2Abbr,
            "startTimeEpochMs" to fixture.startTimeEpochMs,
            "status" to fixture.status.name,
            "liveCounter" to fixture.liveCounter,
            "team1Score" to fixture.team1Score,
            "team2Score" to fixture.team2Score,
            "streams" to fixture.streams.map { it.toFirestoreMap() },
            "isManual" to isManual,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        database.collection("matchOverrides")
            .document(fixture.id)
            .set(record, SetOptions.merge())
            .awaitResult()
    }

    suspend fun deleteMatchOverride(matchId: String) {
        val database = checkNotNull(firestore) { "Firebase Firestore is not configured in this APK." }
        database.collection("matchOverrides").document(matchId).delete().awaitResult()
    }

    fun observePublicMatchOverrides(
        onUpdate: (Map<String, MatchAdminOverride>) -> Unit
    ): ListenerRegistration? {
        val database = firestore ?: return null
        return database.collection("matchOverrides")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    onUpdate(snapshot.documents.associate { it.id to it.toMatchAdminOverride() })
                }
            }
    }

    private fun M3uChannel.toFirestoreMap(): Map<String, Any?> {
        val sources = streams.ifEmpty {
            listOf(
                StreamSource(
                    id = "primary_$id",
                    name = name,
                    qualityLabel = format.name,
                    streamUrl = streamUrl,
                    format = format,
                    isDemo = false
                )
            )
        }
        return mapOf(
            "name" to name,
            "category" to group,
            "logoUrl" to logoUrl,
            "streamUrl" to (sources.firstOrNull()?.streamUrl ?: streamUrl),
            "format" to (sources.firstOrNull()?.format ?: format).name,
            "quality" to sources.firstOrNull()?.qualityLabel.orEmpty(),
            "streams" to sources.map { it.toFirestoreMap() },
            "isActive" to enabled,
            "sortOrder" to displayOrder,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun StreamSource.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "qualityLabel" to qualityLabel,
        "streamUrl" to streamUrl,
        "format" to format.name,
        "bitrateInfo" to bitrateInfo,
        "isDemo" to isDemo
    )

    private fun DocumentSnapshot.toMatchAdminOverride(): MatchAdminOverride {
        val team1 = getString("team1Name").orEmpty()
        val team2 = getString("team2Name").orEmpty()
        return MatchAdminOverride(
            id = id,
            title = getString("title").orEmpty(),
            sport = runCatching { SportType.valueOf(getString("sport") ?: "FOOTBALL") }
                .getOrDefault(SportType.FOOTBALL),
            league = getString("league").orEmpty(),
            team1Name = team1,
            team1LogoUrl = getString("team1LogoUrl"),
            team1Abbr = getString("team1Abbr") ?: team1.take(3).uppercase(),
            team2Name = team2,
            team2LogoUrl = getString("team2LogoUrl"),
            team2Abbr = getString("team2Abbr") ?: team2.take(3).uppercase(),
            startTimeEpochMs = getLong("startTimeEpochMs") ?: 0L,
            status = runCatching { MatchStatus.valueOf(getString("status") ?: "UPCOMING") }
                .getOrDefault(MatchStatus.UPCOMING),
            liveCounter = getString("liveCounter")?.takeIf { it.isNotBlank() },
            team1Score = getString("team1Score")?.takeIf { it.isNotBlank() },
            team2Score = getString("team2Score")?.takeIf { it.isNotBlank() },
            streams = decodeStreamSources(get("streams")),
            isManual = getBoolean("isManual") ?: false
        )
    }

    private fun decodeStreamSources(raw: Any?): List<StreamSource> {
        val values = raw as? List<*> ?: return emptyList()
        return values.mapNotNull { item ->
            val value = item as? Map<*, *> ?: return@mapNotNull null
            val url = value["streamUrl"] as? String ?: return@mapNotNull null
            val streamName = value["name"] as? String ?: "Live stream"
            StreamSource(
                id = value["id"] as? String ?: "stream_${url.hashCode()}",
                name = streamName,
                qualityLabel = value["qualityLabel"] as? String ?: "LIVE",
                streamUrl = url,
                format = parseFormat(value["format"] as? String),
                bitrateInfo = value["bitrateInfo"] as? String ?: "",
                isDemo = value["isDemo"] as? Boolean ?: false
            )
        }
    }

    private fun parseFormat(value: String?): StreamFormat =
        runCatching { StreamFormat.valueOf(value ?: "HLS") }.getOrDefault(StreamFormat.HLS)
}

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
                Result.success("Reachable • HTTP ${response.code}")
            } else {
                Result.failure(IOException("Server returned HTTP ${response.code}."))
            }
        }
    } catch (error: Exception) {
        Result.failure(IOException("Could not reach this stream (${error::class.simpleName ?: "network error"})."))
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IOException("Firebase request failed.")
                )
            }
        }
    }
