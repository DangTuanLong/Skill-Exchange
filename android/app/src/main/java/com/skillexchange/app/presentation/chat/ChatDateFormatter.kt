package com.skillexchange.app.presentation.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ChatDateFormatter {

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    private val FULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun isSameDay(
        time1: Long,
        time2: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val d1 = Instant.ofEpochMilli(time1).atZone(zoneId).toLocalDate()
        val d2 = Instant.ofEpochMilli(time2).atZone(zoneId).toLocalDate()
        return d1 == d2
    }

    fun formatMessageTime(
        timestamp: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        return TIME_FORMATTER.withZone(zoneId).format(Instant.ofEpochMilli(timestamp))
    }

    fun getDaySeparatorText(
        timestamp: Long,
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
        val today = nowInstant.atZone(zoneId).toLocalDate()

        return when {
            date == today -> "Hôm nay"
            date == today.minusDays(1) -> "Hôm qua"
            else -> FULL_DATE_FORMATTER.withZone(zoneId).format(date)
        }
    }

    fun formatChatListTime(
        timestamp: Long?,
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestamp == null || timestamp <= 0) return ""
        val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
        val today = nowInstant.atZone(zoneId).toLocalDate()

        return when {
            date == today -> TIME_FORMATTER.withZone(zoneId).format(Instant.ofEpochMilli(timestamp))
            date.year == today.year -> DAY_MONTH_FORMATTER.withZone(zoneId).format(date)
            else -> FULL_DATE_FORMATTER.withZone(zoneId).format(date)
        }
    }
}

