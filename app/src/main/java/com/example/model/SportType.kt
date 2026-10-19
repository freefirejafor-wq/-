package com.example.model

enum class SportType(val displayName: String, val badgeCount: Int) {
    ALL("All", 29),
    CRICKET("Cricket", 6),
    FOOTBALL("Football", 23)
}

enum class MatchStatus(val label: String) {
    ALL("All"),
    LIVE("Live"),
    UPCOMING("Upcoming"),
    FINISHED("Finished")
}

enum class StreamFormat {
    HLS,
    DASH,
    PROGRESSIVE
}
