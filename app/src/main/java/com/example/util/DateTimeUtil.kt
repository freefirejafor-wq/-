package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtil {
    private val dhakaTimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    fun formatTimeDhaka(epochMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = dhakaTimeZone
        }
        return sdf.format(Date(epochMs))
    }

    fun formatDateDhaka(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = dhakaTimeZone
        }
        return sdf.format(Date(epochMs))
    }

    fun calculateStartsIn(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = epochMs - nowMs
        if (diffMs <= 0) return "Status update pending"

        val hours = diffMs / (1000 * 60 * 60)
        val days = hours / 24

        return when {
            days > 0 -> "Starts in $days days"
            hours > 0 -> "Starts in $hours hours"
            else -> {
                val mins = (diffMs / (1000 * 60)).coerceAtLeast(1)
                "Starts in $mins mins"
            }
        }
    }
}
