package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.exchange.RescheduleStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.*

class RescheduleLogicHelperTest {

    private val now = LocalDateTime.of(2026, 11, 4, 10, 0, 0)
    private val senderId = "user-sender"
    private val receiverId = "user-receiver"
    private val otherId = "user-stranger"

    // --- UUID Parsing ---

    @Test
    fun `parseExchangeUuid - succeeds on valid UUID`() {
        val uuid = UUID.randomUUID()
        val parsed = RescheduleLogicHelper.parseExchangeUuid(uuid.toString())
        assertEquals(uuid, parsed)
    }

    @Test
    fun `parseExchangeUuid - throws RescheduleNotFoundException on invalid UUID`() {
        assertFailsWith<RescheduleNotFoundException> {
            RescheduleLogicHelper.parseExchangeUuid("invalid-uuid-string")
        }
    }

    @Test
    fun `parseProposalUuid - succeeds on valid UUID`() {
        val uuid = UUID.randomUUID()
        val parsed = RescheduleLogicHelper.parseProposalUuid(uuid.toString())
        assertEquals(uuid, parsed)
    }

    @Test
    fun `parseProposalUuid - throws RescheduleNotFoundException on invalid UUID`() {
        assertFailsWith<RescheduleNotFoundException> {
            RescheduleLogicHelper.parseProposalUuid("invalid-proposal-id")
        }
    }

    // --- Participant Validation ---

    @Test
    fun `validateParticipant - allows sender and receiver`() {
        RescheduleLogicHelper.validateParticipant(senderId, senderId, receiverId)
        RescheduleLogicHelper.validateParticipant(receiverId, senderId, receiverId)
    }

    @Test
    fun `validateParticipant - rejects third party`() {
        val ex = assertFailsWith<RescheduleForbiddenException> {
            RescheduleLogicHelper.validateParticipant(otherId, senderId, receiverId)
        }
        assertEquals(ErrorCodes.NOT_PARTICIPANT, ex.errorCode)
    }

    // --- Exchange Status Validation ---

    @Test
    fun `validateExchangeAccepted - allows ACCEPTED exchange`() {
        RescheduleLogicHelper.validateExchangeAccepted(ExchangeStatus.ACCEPTED.name)
    }

    @Test
    fun `validateExchangeAccepted - rejects PENDING, COMPLETED, CANCELLED`() {
        for (status in listOf(ExchangeStatus.PENDING.name, ExchangeStatus.COMPLETED.name, ExchangeStatus.CANCELLED.name, ExchangeStatus.REJECTED.name)) {
            val ex = assertFailsWith<RescheduleConflictException> {
                RescheduleLogicHelper.validateExchangeAccepted(status)
            }
            assertEquals(ErrorCodes.EXCHANGE_NOT_ACTIVE, ex.errorCode)
        }
    }

    // --- New Scheduled At Validation ---

    @Test
    fun `validateNewScheduledAt - accepts time more than 15 minutes ahead`() {
        val validTime = now.plusMinutes(16)
        val result = RescheduleLogicHelper.validateNewScheduledAt(validTime.toString(), now)
        assertEquals(validTime, result)
    }

    @Test
    fun `validateNewScheduledAt - rejects invalid datetime format`() {
        val ex = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.validateNewScheduledAt("invalid-datetime", now)
        }
        assertEquals(ErrorCodes.VALIDATION_FAILED, ex.errorCode)
    }

    @Test
    fun `validateNewScheduledAt - rejects time in the past`() {
        val pastTime = now.minusHours(1)
        val ex = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.validateNewScheduledAt(pastTime.toString(), now)
        }
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, ex.errorCode)
    }

    @Test
    fun `validateNewScheduledAt - rejects time within 15 minutes from now`() {
        val tooSoon = now.plusMinutes(14)
        val ex = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.validateNewScheduledAt(tooSoon.toString(), now)
        }
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, ex.errorCode)
    }

    // --- Reason Validation ---

    @Test
    fun `validateReason - accepts null, empty, and up to 200 chars`() {
        RescheduleLogicHelper.validateReason(null)
        RescheduleLogicHelper.validateReason("")
        RescheduleLogicHelper.validateReason("a".repeat(200))
    }

    @Test
    fun `validateReason - rejects reason longer than 200 chars`() {
        val ex = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.validateReason("a".repeat(201))
        }
        assertEquals(ErrorCodes.VALIDATION_FAILED, ex.errorCode)
    }

    // --- Proposal Limit & Anti-Spam (Cancelling then re-proposing) ---

    @Test
    fun `checkProposalLimit - allows counts 0, 1, 2`() {
        RescheduleLogicHelper.checkProposalLimit(0)
        RescheduleLogicHelper.checkProposalLimit(1)
        RescheduleLogicHelper.checkProposalLimit(2)
    }

    @Test
    fun `checkProposalLimit - rejects count 3 or higher`() {
        val ex3 = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.checkProposalLimit(3)
        }
        assertEquals(ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED, ex3.errorCode)

        val ex4 = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.checkProposalLimit(4)
        }
        assertEquals(ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED, ex4.errorCode)
    }

    @Test
    fun `anti-spam - total count across all historical proposals prevents spamming cancel and re-propose`() {
        // Giả sử exchange đã có 3 đề xuất trong lịch sử:
        // 1 CANCELLED, 1 REJECTED, 1 EXPIRED (hoặc bất kỳ trạng thái nào).
        // Quy tắc TASK-068: đếm giới hạn 3 trên MỌI đề xuất của exchange (kể cả hủy/hết hạn).
        val historicalProposals = listOf(
            RescheduleStatus.CANCELLED,
            RescheduleStatus.REJECTED,
            RescheduleStatus.EXPIRED
        )
        val totalHistoricalCount = historicalProposals.size.toLong()

        // Lần đề xuất thứ 4 phải bị chặn ngay lập tức
        val ex = assertFailsWith<RescheduleValidationException> {
            RescheduleLogicHelper.checkProposalLimit(totalHistoricalCount)
        }
        assertEquals(ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED, ex.errorCode)
        assertTrue(ex.message!!.contains("3 lần"))
    }

    // --- Lazy Expiration ---

    @Test
    fun `isProposalExpired - returns true if created more than 24 hours ago`() {
        val createdAt = now.minusHours(24).minusSeconds(1)
        val newScheduledAt = now.plusDays(2)
        val expired = RescheduleLogicHelper.isProposalExpired(RescheduleStatus.PENDING.name, createdAt, newScheduledAt, now)
        assertTrue(expired)
    }

    @Test
    fun `isProposalExpired - returns true if new_scheduled_at has already passed`() {
        val createdAt = now.minusHours(2)
        val newScheduledAt = now.minusMinutes(1)
        val expired = RescheduleLogicHelper.isProposalExpired(RescheduleStatus.PENDING.name, createdAt, newScheduledAt, now)
        assertTrue(expired)
    }

    @Test
    fun `isProposalExpired - returns false if within 24h and new_scheduled_at is in future`() {
        val createdAt = now.minusHours(10)
        val newScheduledAt = now.plusHours(5)
        val expired = RescheduleLogicHelper.isProposalExpired(RescheduleStatus.PENDING.name, createdAt, newScheduledAt, now)
        assertFalse(expired)
    }

    @Test
    fun `isProposalExpired - returns false if proposal is not PENDING`() {
        val createdAt = now.minusHours(50)
        val newScheduledAt = now.minusHours(30)
        // Already ACCEPTED, REJECTED, or CANCELLED proposals do not re-expire
        assertFalse(RescheduleLogicHelper.isProposalExpired(RescheduleStatus.ACCEPTED.name, createdAt, newScheduledAt, now))
        assertFalse(RescheduleLogicHelper.isProposalExpired(RescheduleStatus.REJECTED.name, createdAt, newScheduledAt, now))
        assertFalse(RescheduleLogicHelper.isProposalExpired(RescheduleStatus.CANCELLED.name, createdAt, newScheduledAt, now))
    }

    // --- Accept Concurrency & Idempotency ---

    @Test
    fun `validateAcceptEligibility - allows partner to accept PENDING proposal`() {
        val canProceed = RescheduleLogicHelper.validateAcceptEligibility(
            callerId = receiverId,
            proposerId = senderId,
            status = RescheduleStatus.PENDING.name
        )
        assertTrue(canProceed)
    }

    @Test
    fun `validateAcceptEligibility - forbids proposer from accepting own proposal`() {
        val ex = assertFailsWith<RescheduleForbiddenException> {
            RescheduleLogicHelper.validateAcceptEligibility(
                callerId = senderId,
                proposerId = senderId,
                status = RescheduleStatus.PENDING.name
            )
        }
        assertTrue(ex.message!!.contains("chính mình"))
    }

    @Test
    fun `validateAcceptEligibility - returns false idempotently if already ACCEPTED`() {
        val canProceed = RescheduleLogicHelper.validateAcceptEligibility(
            callerId = receiverId,
            proposerId = senderId,
            status = RescheduleStatus.ACCEPTED.name
        )
        assertFalse(canProceed)
    }

    @Test
    fun `validateAcceptEligibility - rejects non-pending statuses`() {
        for (status in listOf(RescheduleStatus.CANCELLED.name, RescheduleStatus.REJECTED.name, RescheduleStatus.EXPIRED.name)) {
            val ex = assertFailsWith<RescheduleConflictException> {
                RescheduleLogicHelper.validateAcceptEligibility(
                    callerId = receiverId,
                    proposerId = senderId,
                    status = status
                )
            }
            assertEquals(ErrorCodes.PROPOSAL_NOT_PENDING, ex.errorCode)
        }
    }

    // --- Reject Concurrency & Idempotency ---

    @Test
    fun `validateRejectEligibility - allows partner to reject PENDING proposal`() {
        val canProceed = RescheduleLogicHelper.validateRejectEligibility(
            callerId = receiverId,
            proposerId = senderId,
            status = RescheduleStatus.PENDING.name
        )
        assertTrue(canProceed)
    }

    @Test
    fun `validateRejectEligibility - forbids proposer from rejecting own proposal`() {
        val ex = assertFailsWith<RescheduleForbiddenException> {
            RescheduleLogicHelper.validateRejectEligibility(
                callerId = senderId,
                proposerId = senderId,
                status = RescheduleStatus.PENDING.name
            )
        }
        assertTrue(ex.message!!.contains("chính mình"))
    }

    @Test
    fun `validateRejectEligibility - returns false idempotently if already REJECTED`() {
        val canProceed = RescheduleLogicHelper.validateRejectEligibility(
            callerId = receiverId,
            proposerId = senderId,
            status = RescheduleStatus.REJECTED.name
        )
        assertFalse(canProceed)
    }

    @Test
    fun `validateRejectEligibility - rejects non-pending statuses`() {
        for (status in listOf(RescheduleStatus.ACCEPTED.name, RescheduleStatus.CANCELLED.name, RescheduleStatus.EXPIRED.name)) {
            val ex = assertFailsWith<RescheduleConflictException> {
                RescheduleLogicHelper.validateRejectEligibility(
                    callerId = receiverId,
                    proposerId = senderId,
                    status = status
                )
            }
            assertEquals(ErrorCodes.PROPOSAL_NOT_PENDING, ex.errorCode)
        }
    }

    // --- Cancel Concurrency & Idempotency ---

    @Test
    fun `validateCancelEligibility - allows proposer to cancel PENDING proposal`() {
        val canProceed = RescheduleLogicHelper.validateCancelEligibility(
            callerId = senderId,
            proposerId = senderId,
            status = RescheduleStatus.PENDING.name
        )
        assertTrue(canProceed)
    }

    @Test
    fun `validateCancelEligibility - forbids partner from cancelling proposer's proposal`() {
        val ex = assertFailsWith<RescheduleForbiddenException> {
            RescheduleLogicHelper.validateCancelEligibility(
                callerId = receiverId,
                proposerId = senderId,
                status = RescheduleStatus.PENDING.name
            )
        }
        assertTrue(ex.message!!.contains("Chỉ người tạo"))
    }

    @Test
    fun `validateCancelEligibility - returns false idempotently if already CANCELLED`() {
        val canProceed = RescheduleLogicHelper.validateCancelEligibility(
            callerId = senderId,
            proposerId = senderId,
            status = RescheduleStatus.CANCELLED.name
        )
        assertFalse(canProceed)
    }

    @Test
    fun `validateCancelEligibility - rejects non-pending statuses`() {
        for (status in listOf(RescheduleStatus.ACCEPTED.name, RescheduleStatus.REJECTED.name, RescheduleStatus.EXPIRED.name)) {
            val ex = assertFailsWith<RescheduleConflictException> {
                RescheduleLogicHelper.validateCancelEligibility(
                    callerId = senderId,
                    proposerId = senderId,
                    status = status
                )
            }
            assertEquals(ErrorCodes.PROPOSAL_NOT_PENDING, ex.errorCode)
        }
    }

    // --- Appointment Reminder UTC Formatting & Re-reminding ---

    @Test
    fun `formatScheduledAtForReminder - formats to exact ISO-8601 UTC string`() {
        val dt = LocalDateTime.of(2026, 10, 28, 19, 0, 0)
        val formatted = RescheduleLogicHelper.formatScheduledAtForReminder(dt)
        assertEquals("2026-10-28T19:00:00Z", formatted)
    }

    @Test
    fun `reschedule re-reminding produces distinct reminder keys`() {
        // Khi exchange được dời lịch từ 19:00 sang 21:00:
        val oldScheduledAt = LocalDateTime.of(2026, 10, 28, 19, 0, 0)
        val newScheduledAt = LocalDateTime.of(2026, 10, 28, 21, 0, 0)

        val oldFormatted = RescheduleLogicHelper.formatScheduledAtForReminder(oldScheduledAt)
        val newFormatted = RescheduleLogicHelper.formatScheduledAtForReminder(newScheduledAt)

        assertNotEquals(oldFormatted, newFormatted)
        assertEquals("2026-10-28T19:00:00Z", oldFormatted)
        assertEquals("2026-10-28T21:00:00Z", newFormatted)

        // Nhờ index idx_notifications_unique_appointment dựa trên (data->>'scheduledAt'),
        // thông báo cho giờ mới không bị coi là duplicate của thông báo cho giờ cũ!
        val exchangeId = UUID.randomUUID().toString()
        val oldPayload = """{"exchangeId":"$exchangeId","scheduledAt":"$oldFormatted"}"""
        val newPayload = """{"exchangeId":"$exchangeId","scheduledAt":"$newFormatted"}"""
        assertNotEquals(oldPayload, newPayload)
    }

    // --- Notification Formatting Tests (BUSINESS_RULES.md §6) ---

    @Test
    fun `notification body formatting - matches BUSINESS_RULES section 6 specifications`() {
        val dt = LocalDateTime.of(2026, 11, 10, 14, 30, 0)
        val callerName = "Nguyễn Văn A"

        // Proposed: "{Name} đề xuất đổi lịch hẹn sang {time}"
        val proposedBody = RescheduleLogicHelper.formatProposedNotificationBody(callerName, dt)
        assertEquals("Nguyễn Văn A đề xuất đổi lịch hẹn sang 14:30 10/11", proposedBody)

        // Accepted: "{Name} đã đồng ý đổi lịch hẹn sang {time}"
        val acceptedBody = RescheduleLogicHelper.formatAcceptedNotificationBody(callerName, dt)
        assertEquals("Nguyễn Văn A đã đồng ý đổi lịch hẹn sang 14:30 10/11", acceptedBody)

        // Rejected: "{Name} đã từ chối đề xuất đổi lịch hẹn"
        val rejectedBody = RescheduleLogicHelper.formatRejectedNotificationBody(callerName)
        assertEquals("Nguyễn Văn A đã từ chối đề xuất đổi lịch hẹn", rejectedBody)

        // Data payload
        val exchangeId = UUID.randomUUID().toString()
        val proposalId = UUID.randomUUID().toString()
        val payload = RescheduleLogicHelper.formatRescheduleDataPayload(exchangeId, proposalId)
        assertEquals("""{"exchangeId":"$exchangeId","proposalId":"$proposalId"}""", payload)
    }
}

