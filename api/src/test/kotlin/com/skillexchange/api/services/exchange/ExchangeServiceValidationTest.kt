package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.plugins.ValidationException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExchangeServiceValidationTest {

    private val exchangeService = ExchangeService()

    private fun futureIso(daysAhead: Long = 2): String =
        LocalDateTime.now().plusDays(daysAhead).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun pastIso(daysBehind: Long = 1): String =
        LocalDateTime.now().minusDays(daysBehind).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @Test
    fun `validateCreateExchangeInput - throws ValidationException on self-exchange`() {
        val req = CreateExchangeRequest(
            receiverId = "user-alice",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = futureIso()
        )

        val ex = assertFailsWith<ValidationException> {
            exchangeService.validateCreateExchangeInput("user-alice", req)
        }
        assertEquals("Không thể gửi yêu cầu trao đổi cho chính mình", ex.message)
    }

    @Test
    fun `validateCreateExchangeInput - throws ValidationException on message exceeding 1000 characters`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = futureIso(),
            message = "A".repeat(1001)
        )

        val ex = assertFailsWith<ValidationException> {
            exchangeService.validateCreateExchangeInput("user-alice", req)
        }
        assertEquals("Lời nhắn không được vượt quá 1000 ký tự", ex.message)
    }

    @Test
    fun `validateCreateExchangeInput - throws ValidationException on invalid durationMinutes`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 45, // Not in 30, 60, 90, 120
            meetingMode = "ONLINE",
            scheduledAt = futureIso()
        )

        val ex = assertFailsWith<ValidationException> {
            exchangeService.validateCreateExchangeInput("user-alice", req)
        }
        assertEquals("Thời lượng trao đổi phải là 30, 60, 90 hoặc 120 phút", ex.message)
    }

    @Test
    fun `validateCreateExchangeInput - throws ValidationException on invalid meetingMode`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "HYBRID", // Invalid mode
            scheduledAt = futureIso()
        )

        val ex = assertFailsWith<ValidationException> {
            exchangeService.validateCreateExchangeInput("user-alice", req)
        }
        assertEquals("Chế độ gặp gỡ không hợp lệ (phải là ONLINE, IN_PERSON hoặc UNDECIDED)", ex.message)
    }

    @Test
    fun `validateCreateExchangeInput - throws ValidationException on past scheduledAt`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = pastIso()
        )

        val ex = assertFailsWith<ValidationException> {
            exchangeService.validateCreateExchangeInput("user-alice", req)
        }
        assertEquals("Thời gian hẹn phải ở tương lai", ex.message)
    }

    @Test
    fun `validateCreateExchangeInput - passes on valid input without exception`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = futureIso(),
            message = "Chào bạn, mình muốn trao đổi kỹ năng."
        )

        // Should not throw
        exchangeService.validateCreateExchangeInput("user-alice", req)
    }
}

