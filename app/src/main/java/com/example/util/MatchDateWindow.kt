package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Match-date window shared by the cricket and ESPN football feeds. */
object MatchDateWindow {
    private const val DHAKA_TIME_ZONE = "Asia/Dhaka"

    /** Includes yesterday, today and tomorrow in Asia/Dhaka. */
    fun contains(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val todayStart = dhakaDayStart(nowMs)
        val start = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val endExclusive = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 2)
        }.timeInMillis
        return timestampMs >= start && timestampMs < endExclusive
    }

    /** ESPN indexes scoreboard days in its own calendar; include a UTC buffer, then filter in Dhaka. */
    fun espnQueryDates(nowMs: Long = System.currentTimeMillis()): List<String> {
        val todayStart = dhakaDayStart(nowMs)
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(DHAKA_TIME_ZONE)
        }
        return (-2..1).map { offset ->
            val day = (todayStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            formatter.format(day.time)
        }
    }

    private fun dhakaDayStart(nowMs: Long): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone(DHAKA_TIME_ZONE)
    ).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
