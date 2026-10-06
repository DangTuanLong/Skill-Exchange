package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.exchange.ExchangeStatus
import java.time.LocalDateTime
import kotlin.test.*

class ExchangeStateMachineTest {

    private val senderId = "user-sender-111"
    private val receiverId = "user-receiver-222"
    private val strangerId = "user-stranger-999"
    private val testTime = LocalDateTime.of(2026, 10, 10, 10, 0, 0)

    // ─────────────────────────────────────────────────────────────
    // 1. ACCEPT tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `accept - Receiver transitions PENDING to ACCEPTED`() {
        val result = ExchangeStateMachine.accept(
            currentStatus = ExchangeStatus.PENDING,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.ACCEPTED, result.newStatus)
        assertTrue(result.isChanged)
    }

    @Test
    fun `accept - Sender throws 403 Forbidden`() {
        assertFailsWith<ExchangeForbiddenException> {
            ExchangeStateMachine.accept(
                currentStatus = ExchangeStatus.PENDING,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = senderId,
                senderCompletedAt = null,
                receiverCompletedAt = null,
                cancellationReason = null
            )
        }
    }

    @Test
    fun `accept - Stranger throws 403 Forbidden`() {
        assertFailsWith<ExchangeForbiddenException> {
            ExchangeStateMachine.accept(
                currentStatus = ExchangeStatus.PENDING,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = strangerId,
                senderCompletedAt = null,
                receiverCompletedAt = null,
                cancellationReason = null
            )
        }
    }

    @Test
    fun `accept - Idempotent when already ACCEPTED`() {
        val result = ExchangeStateMachine.accept(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.ACCEPTED, result.newStatus)
        assertFalse(result.isChanged)
    }

    @Test
    fun `accept - Throws 409 Conflict when in terminal states`() {
        val terminalStates = listOf(ExchangeStatus.REJECTED, ExchangeStatus.CANCELLED, ExchangeStatus.COMPLETED)
        for (status in terminalStates) {
            assertFailsWith<ExchangeConflictException> {
                ExchangeStateMachine.accept(
                    currentStatus = status,
                    senderId = senderId,
                    receiverId = receiverId,
                    callerUserId = receiverId,
                    senderCompletedAt = null,
                    receiverCompletedAt = null,
                    cancellationReason = null
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. REJECT tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `reject - Receiver transitions PENDING to REJECTED`() {
        val result = ExchangeStateMachine.reject(
            currentStatus = ExchangeStatus.PENDING,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.REJECTED, result.newStatus)
        assertTrue(result.isChanged)
    }

    @Test
    fun `reject - Sender throws 403 Forbidden`() {
        assertFailsWith<ExchangeForbiddenException> {
            ExchangeStateMachine.reject(
                currentStatus = ExchangeStatus.PENDING,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = senderId,
                senderCompletedAt = null,
                receiverCompletedAt = null,
                cancellationReason = null
            )
        }
    }

    @Test
    fun `reject - Idempotent when already REJECTED`() {
        val result = ExchangeStateMachine.reject(
            currentStatus = ExchangeStatus.REJECTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.REJECTED, result.newStatus)
        assertFalse(result.isChanged)
    }

    @Test
    fun `reject - Throws 409 Conflict when in non-pending states`() {
        val nonPendingStates = listOf(ExchangeStatus.ACCEPTED, ExchangeStatus.CANCELLED, ExchangeStatus.COMPLETED)
        for (status in nonPendingStates) {
            assertFailsWith<ExchangeConflictException> {
                ExchangeStateMachine.reject(
                    currentStatus = status,
                    senderId = senderId,
                    receiverId = receiverId,
                    callerUserId = receiverId,
                    senderCompletedAt = null,
                    receiverCompletedAt = null,
                    cancellationReason = null
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. CANCEL tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `cancel - Sender can cancel PENDING request`() {
        val result = ExchangeStateMachine.cancel(
            currentStatus = ExchangeStatus.PENDING,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = senderId,
            reason = "Bận đột xuất",
            senderCompletedAt = null,
            receiverCompletedAt = null,
            existingReason = null
        )

        assertEquals(ExchangeStatus.CANCELLED, result.newStatus)
        assertEquals("Bận đột xuất", result.cancellationReason)
        assertTrue(result.isChanged)
    }

    @Test
    fun `cancel - Receiver CANNOT cancel PENDING request (must reject)`() {
        assertFailsWith<ExchangeForbiddenException> {
            ExchangeStateMachine.cancel(
                currentStatus = ExchangeStatus.PENDING,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = receiverId,
                reason = "Không thích",
                senderCompletedAt = null,
                receiverCompletedAt = null,
                existingReason = null
            )
        }
    }

    @Test
    fun `cancel - Both Sender and Receiver can cancel ACCEPTED request`() {
        val res1 = ExchangeStateMachine.cancel(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = senderId,
            reason = "Lý do từ sender",
            senderCompletedAt = null,
            receiverCompletedAt = null,
            existingReason = null
        )
        assertEquals(ExchangeStatus.CANCELLED, res1.newStatus)
        assertEquals("Lý do từ sender", res1.cancellationReason)

        val res2 = ExchangeStateMachine.cancel(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            reason = "Lý do từ receiver",
            senderCompletedAt = null,
            receiverCompletedAt = null,
            existingReason = null
        )
        assertEquals(ExchangeStatus.CANCELLED, res2.newStatus)
        assertEquals("Lý do từ receiver", res2.cancellationReason)
    }

    @Test
    fun `cancel - Idempotent when already CANCELLED`() {
        val result = ExchangeStateMachine.cancel(
            currentStatus = ExchangeStatus.CANCELLED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = senderId,
            reason = "Hủy lần 2",
            senderCompletedAt = null,
            receiverCompletedAt = null,
            existingReason = "Lý do cũ"
        )
        assertEquals(ExchangeStatus.CANCELLED, result.newStatus)
        assertEquals("Lý do cũ", result.cancellationReason)
        assertFalse(result.isChanged)
    }

    @Test
    fun `cancel - Throws 409 Conflict when COMPLETED or REJECTED`() {
        assertFailsWith<ExchangeConflictException> {
            ExchangeStateMachine.cancel(
                currentStatus = ExchangeStatus.COMPLETED,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = senderId,
                reason = null,
                senderCompletedAt = testTime,
                receiverCompletedAt = testTime,
                existingReason = null
            )
        }
        assertFailsWith<ExchangeConflictException> {
            ExchangeStateMachine.cancel(
                currentStatus = ExchangeStatus.REJECTED,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = senderId,
                reason = null,
                senderCompletedAt = null,
                receiverCompletedAt = null,
                existingReason = null
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. COMPLETE tests (2-party confirmation)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `complete - First confirmation sets timestamp and keeps ACCEPTED`() {
        // Receiver confirms first
        val step1 = ExchangeStateMachine.complete(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            now = testTime,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.ACCEPTED, step1.newStatus)
        assertNull(step1.senderCompletedAt)
        assertEquals(testTime, step1.receiverCompletedAt)
        assertTrue(step1.isChanged)

        // Receiver confirms again -> idempotent
        val step1Again = ExchangeStateMachine.complete(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = receiverId,
            now = testTime.plusMinutes(5),
            senderCompletedAt = null,
            receiverCompletedAt = testTime,
            cancellationReason = null
        )
        assertEquals(ExchangeStatus.ACCEPTED, step1Again.newStatus)
        assertEquals(testTime, step1Again.receiverCompletedAt)
        assertFalse(step1Again.isChanged)

        // Sender confirms second -> becomes COMPLETED!
        val step2Time = testTime.plusHours(1)
        val step2 = ExchangeStateMachine.complete(
            currentStatus = ExchangeStatus.ACCEPTED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = senderId,
            now = step2Time,
            senderCompletedAt = null,
            receiverCompletedAt = testTime,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.COMPLETED, step2.newStatus)
        assertEquals(step2Time, step2.senderCompletedAt)
        assertEquals(testTime, step2.receiverCompletedAt)
        assertTrue(step2.isChanged)
    }

    @Test
    fun `complete - Idempotent when already COMPLETED`() {
        val result = ExchangeStateMachine.complete(
            currentStatus = ExchangeStatus.COMPLETED,
            senderId = senderId,
            receiverId = receiverId,
            callerUserId = senderId,
            now = testTime,
            senderCompletedAt = testTime,
            receiverCompletedAt = testTime,
            cancellationReason = null
        )

        assertEquals(ExchangeStatus.COMPLETED, result.newStatus)
        assertFalse(result.isChanged)
    }

    @Test
    fun `complete - Throws 409 Conflict when not in ACCEPTED state`() {
        val invalidStates = listOf(ExchangeStatus.PENDING, ExchangeStatus.REJECTED, ExchangeStatus.CANCELLED)
        for (status in invalidStates) {
            assertFailsWith<ExchangeConflictException> {
                ExchangeStateMachine.complete(
                    currentStatus = status,
                    senderId = senderId,
                    receiverId = receiverId,
                    callerUserId = senderId,
                    now = testTime,
                    senderCompletedAt = null,
                    receiverCompletedAt = null,
                    cancellationReason = null
                )
            }
        }
    }

    @Test
    fun `complete - Stranger throws 403 Forbidden`() {
        assertFailsWith<ExchangeForbiddenException> {
            ExchangeStateMachine.complete(
                currentStatus = ExchangeStatus.ACCEPTED,
                senderId = senderId,
                receiverId = receiverId,
                callerUserId = strangerId,
                now = testTime,
                senderCompletedAt = null,
                receiverCompletedAt = null,
                cancellationReason = null
            )
        }
    }
}
