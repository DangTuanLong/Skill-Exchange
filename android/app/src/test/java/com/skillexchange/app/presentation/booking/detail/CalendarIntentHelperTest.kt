package com.skillexchange.app.presentation.booking.detail

import android.content.Intent
import android.provider.CalendarContract
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CalendarIntentHelperTest {

    private val baseExchange = ExchangeRequest(
        id = "exchange-123",
        senderId = "user-sender",
        receiverId = "user-receiver",
        senderName = "Nguyễn Văn A",
        senderAvatarUrl = "https://example.com/a.jpg",
        receiverName = "Trần Thị B",
        receiverAvatarUrl = "https://example.com/b.jpg",
        skillOfferedId = 1,
        skillOfferedName = "Kotlin",
        skillWantedId = 2,
        skillWantedName = "Python",
        status = ExchangeStatus.ACCEPTED,
        durationMinutes = 90,
        meetingMode = MeetingMode.ONLINE,
        message = "Trao đổi online qua Google Meet nhé!",
        cancellationReason = null,
        scheduledAt = "2026-10-28T19:00:00Z",
        senderCompletedAt = null,
        receiverCompletedAt = null,
        createdAt = "2026-10-25T10:00:00Z",
        updatedAt = "2026-10-25T12:00:00Z"
    )

    @Test
    fun `buildEventData when caller is Sender - formats title, partner name and times correctly`() {
        val eventData = CalendarIntentHelper.buildEventData(baseExchange, "user-sender")

        assertEquals("Trao đổi kỹ năng: [Dạy Kotlin ↔ Học Python]", eventData.title)
        assertTrue(eventData.description.contains("Đối tác: Trần Thị B"))
        assertTrue(eventData.description.contains("Hình thức: Trực tuyến (Online)"))
        assertTrue(eventData.description.contains("Lời nhắn: Trao đổi online qua Google Meet nhé!"))
        assertEquals("Trực tuyến (Online)", eventData.location)

        val expectedBegin = Instant.parse("2026-10-28T19:00:00Z").toEpochMilli()
        val expectedEnd = expectedBegin + 90 * 60 * 1000L
        assertEquals(expectedBegin, eventData.beginTimeMillis)
        assertEquals(expectedEnd, eventData.endTimeMillis)
    }

    @Test
    fun `buildEventData when caller is Receiver - inverts teach and learn skills and shows sender name`() {
        val eventData = CalendarIntentHelper.buildEventData(baseExchange, "user-receiver")

        assertEquals("Trao đổi kỹ năng: [Dạy Python ↔ Học Kotlin]", eventData.title)
        assertTrue(eventData.description.contains("Đối tác: Nguyễn Văn A"))
    }

    @Test
    fun `buildEventData with InPerson meeting mode - sets location to Gặp trực tiếp`() {
        val inPersonExchange = baseExchange.copy(
            meetingMode = MeetingMode.IN_PERSON,
            message = null
        )

        val eventData = CalendarIntentHelper.buildEventData(inPersonExchange, "user-sender")

        assertEquals("Gặp trực tiếp", eventData.location)
        assertTrue(eventData.description.contains("Hình thức: Gặp trực tiếp"))
        // Không chứa dòng Lời nhắn khi message rỗng
        assertTrue(!eventData.description.contains("Lời nhắn:"))
    }

    @Test
    fun `buildEventData when durationMinutes is 0 or negative - defaults to 60 minutes`() {
        val zeroDurationExchange = baseExchange.copy(durationMinutes = 0)

        val eventData = CalendarIntentHelper.buildEventData(zeroDurationExchange, "user-sender")

        val expectedBegin = Instant.parse("2026-10-28T19:00:00Z").toEpochMilli()
        val expectedEnd = expectedBegin + 60 * 60 * 1000L
        assertEquals(expectedEnd, eventData.endTimeMillis)
    }

    @Test
    fun `parseEpochMillis parses valid ISO-8601 UTC string`() {
        val isoUtc = "2026-10-28T19:00:00Z"
        val expected = Instant.parse(isoUtc).toEpochMilli()

        val actual = CalendarIntentHelper.parseEpochMillis(isoUtc)

        assertEquals(expected, actual)
    }

    @Test
    fun `parseEpochMillis fallback on invalid string returns current or positive epoch`() {
        val invalid = "not-a-valid-timestamp"
        val millis = CalendarIntentHelper.parseEpochMillis(invalid)

        assertTrue(millis > 0)
    }

    @Test
    fun `buildCalendarIntent executes without exception and returns intent`() {
        val eventData = CalendarIntentHelper.buildEventData(baseExchange, "user-sender")
        val intent = CalendarIntentHelper.buildCalendarIntent(eventData)

        assertNotNull(intent)
    }
}
