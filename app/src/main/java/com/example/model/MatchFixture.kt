package com.example.model

data class MatchFixture(
    val id: String,
    val title: String,
    val sport: SportType,
    val league: String,
    val team1Name: String,
    val team1LogoUrl: String? = null,
    val team1Abbr: String = team1Name,
    val team2Name: String,
    val team2LogoUrl: String? = null,
    val team2Abbr: String = team2Name,
    val startTimeEpochMs: Long,
    val status: MatchStatus,
    val liveCounter: String? = null, // e.g. "03:26:41" or "72'"
    val startsInText: String? = null, // e.g. "Starts in 2 hours"
    val team1Score: String? = null,
    val team2Score: String? = null,
    val streams: List<StreamSource> = emptyList(),
    val isDemo: Boolean = true
)
