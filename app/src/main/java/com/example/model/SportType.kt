package com.example.model

enum class SportType(val displayName: String, val badgeCount: Int) {
    ALL("All", 46),
    CRICKET("Cricket", 6),
    FOOTBALL("Football", 23),
    BOXING("Boxing", 5),
    BASKETBALL("Basketball", 1),
    HOCKEY("Hockey", 4),
    BASEBALL("Baseball", 3),
    MOTORSPORT("Motorsport", 2),
    TENNIS("Tennis", 2)
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
