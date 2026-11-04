package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RescheduleServiceValidationTest {

    private val fixedNow = LocalDateTime.of(2026, 11, 4, 10, 0, 0)
    private val rescheduleService = RescheduleService(timeProvider = { fixedNow })

    @Test
    fun `createProposal - throws RescheduleNotFoundException on invalid exchangeId format`() {
        val req = CreateRescheduleRequest(
            newScheduledAt = fixedNow.plusHours(1).toString(),
            reason = "Bận đột xuất"
        )
        val ex = assertFailsWith<RescheduleNotFoundException> {
            rescheduleService.createProposal("user-1", "not-a-valid-uuid", req)
        }
        assertEquals("Không tìm thấy yêu cầu trao đổi", ex.message)
    }

    @Test
    fun `createProposal - throws RescheduleValidationException on reason exceeding 200 chars`() {
        val req = CreateRescheduleRequest(
            newScheduledAt = fixedNow.plusHours(1).toString(),
            reason = "a".repeat(201)
        )
        val ex = assertFailsWith<RescheduleValidationException> {
            rescheduleService.createProposal("user-1", UUID.randomUUID().toString(), req)
        }
        assertEquals("Lý do đổi lịch không được vượt quá 200 ký tự.", ex.message)
    }

    @Test
    fun `createProposal - throws RescheduleValidationException on invalid newScheduledAt format`() {
        val req = CreateRescheduleRequest(
            newScheduledAt = "invalid-date-string",
            reason = "Bận việc"
        )
        val ex = assertFailsWith<RescheduleValidationException> {
            rescheduleService.createProposal("user-1", UUID.randomUUID().toString(), req)
        }
        assertEquals(ErrorCodes.VALIDATION_FAILED, ex.errorCode)
    }

    @Test
    fun `createProposal - throws RescheduleValidationException on newScheduledAt in past`() {
        val req = CreateRescheduleRequest(
            newScheduledAt = fixedNow.minusMinutes(30).toString(),
            reason = "Đã qua"
        )
        val ex = assertFailsWith<RescheduleValidationException> {
            rescheduleService.createProposal("user-1", UUID.randomUUID().toString(), req)
        }
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, ex.errorCode)
    }

    @Test
    fun `createProposal - throws RescheduleValidationException on newScheduledAt less than 15 mins ahead`() {
        val req = CreateRescheduleRequest(
            newScheduledAt = fixedNow.plusMinutes(10).toString(),
            reason = "Gấp quá"
        )
        val ex = assertFailsWith<RescheduleValidationException> {
            rescheduleService.createProposal("user-1", UUID.randomUUID().toString(), req)
        }
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, ex.errorCode)
    }

    @Test
    fun `getCurrentProposal - throws RescheduleNotFoundException on invalid exchangeId`() {
        val ex = assertFailsWith<RescheduleNotFoundException> {
            rescheduleService.getCurrentProposal("user-1", "bad-uuid")
        }
        assertEquals("Không tìm thấy yêu cầu trao đổi", ex.message)
    }

    @Test
    fun `acceptProposal - throws RescheduleNotFoundException on invalid proposalId`() {
        val ex = assertFailsWith<RescheduleNotFoundException> {
            rescheduleService.acceptProposal("user-1", "bad-uuid")
        }
        assertEquals("Không tìm thấy đề xuất đổi lịch", ex.message)
    }

    @Test
    fun `rejectProposal - throws RescheduleNotFoundException on invalid proposalId`() {
        val ex = assertFailsWith<RescheduleNotFoundException> {
            rescheduleService.rejectProposal("user-1", "bad-uuid")
        }
        assertEquals("Không tìm thấy đề xuất đổi lịch", ex.message)
    }

    @Test
    fun `cancelProposal - throws RescheduleNotFoundException on invalid proposalId`() {
        val ex = assertFailsWith<RescheduleNotFoundException> {
            rescheduleService.cancelProposal("user-1", "bad-uuid")
        }
        assertEquals("Không tìm thấy đề xuất đổi lịch", ex.message)
    }
}

