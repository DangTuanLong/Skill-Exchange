package com.skillexchange.app.presentation.booking.detail

import android.content.Intent
import android.provider.CalendarContract
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.MeetingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class CalendarEventData(
    val title: String,
    val description: String,
    val location: String,
    val beginTimeMillis: Long,
    val endTimeMillis: Long
)

object CalendarIntentHelper {

    fun buildEventData(exchange: ExchangeRequest, currentUserId: String): CalendarEventData {
        val teachSkill = exchange.teachSkillName(currentUserId)
        val learnSkill = exchange.learnSkillName(currentUserId)
        val title = "Trao đổi kỹ năng: [Dạy $teachSkill ↔ Học $learnSkill]"
        val partnerName = exchange.otherUserName(currentUserId)
        val meetingMode = exchange.meetingMode.displayName()

        val descBuilder = StringBuilder()
        descBuilder.append("Đối tác: $partnerName\n")
        descBuilder.append("Hình thức: $meetingMode\n")
        if (!exchange.message.isNullOrBlank()) {
            descBuilder.append("Lời nhắn: ${exchange.message}\n")
        }

        val location = when (exchange.meetingMode) {
            MeetingMode.ONLINE -> "Trực tuyến (Online)"
            MeetingMode.IN_PERSON -> "Gặp trực tiếp"
            MeetingMode.UNDECIDED -> "Chưa quyết định"
        }

        val beginMillis = parseEpochMillis(exchange.scheduledAt)
        val durationMillis = (exchange.durationMinutes.takeIf { it > 0 } ?: 60) * 60 * 1000L
        val endMillis = beginMillis + durationMillis

        return CalendarEventData(
            title = title,
            description = descBuilder.toString().trimEnd(),
            location = location,
            beginTimeMillis = beginMillis,
            endTimeMillis = endMillis
        )
    }

    fun buildCalendarIntent(eventData: CalendarEventData): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, eventData.title)
            putExtra(CalendarContract.Events.DESCRIPTION, eventData.description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, eventData.location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, eventData.beginTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, eventData.endTimeMillis)
        }
    }

    fun parseEpochMillis(scheduledAt: String): Long {
        return try {
            Instant.parse(scheduledAt).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(scheduledAt)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
