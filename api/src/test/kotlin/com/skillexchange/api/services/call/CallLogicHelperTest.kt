package com.skillexchange.api.services.call

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.call.CallStatus
import com.skillexchange.api.models.call.CallTokenRequest
import com.skillexchange.api.models.call.CallType
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.ValidationException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class CallLogicHelperTest {

    private inline fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            fail("Expected no exception but threw: ${e.message}")
        }
    }

    @Test
    fun `validateInitiateCallRequest parses valid requests`() {
        val validExchangeId = UUID.randomUUID().toString()

        val (typeVoice, uuidVoice) = CallLogicHelper.validateInitiateCallRequest(
            CallTokenRequest(exchangeId = validExchangeId, callType = "VOICE")
        )
        assertEquals(CallType.VOICE, typeVoice)
        assertEquals(validExchangeId, uuidVoice.toString())

        val (typeVideo, uuidVideo) = CallLogicHelper.validateInitiateCallRequest(
            CallTokenRequest(exchangeId = validExchangeId, callType = "video")
        )
        assertEquals(CallType.VIDEO, typeVideo)
        assertEquals(validExchangeId, uuidVideo.toString())
    }

    @Test
    fun `validateInitiateCallRequest throws on invalid callType or exchangeId`() {
        val validExchangeId = UUID.randomUUID().toString()

        val exType = assertThrows<ValidationException> {
            CallLogicHelper.validateInitiateCallRequest(
                CallTokenRequest(exchangeId = validExchangeId, callType = "UNKNOWN")
            )
        }
        assertTrue(exType.message?.contains("Loại cuộc gọi không hợp lệ") == true)

        val exUuid = assertThrows<ValidationException> {
            CallLogicHelper.validateInitiateCallRequest(
                CallTokenRequest(exchangeId = "not-a-uuid", callType = "VOICE")
            )
        }
        assertTrue(exUuid.message?.contains("exchangeId không hợp lệ") == true)
    }

    @Test
    fun `parseSessionUuid validates correctly`() {
        val validUuid = UUID.randomUUID()
        assertEquals(validUuid, CallLogicHelper.parseSessionUuid(validUuid.toString()))

        assertThrows<ValidationException> {
            CallLogicHelper.parseSessionUuid("invalid-uuid")
        }
    }

    @Test
    fun `validateParticipant and determineTargetReceiverId work as expected`() {
        val senderId = "user-sender"
        val receiverId = "user-receiver"

        // Valid participants
        assertDoesNotThrow { CallLogicHelper.validateParticipant(senderId, senderId, receiverId) }
        assertDoesNotThrow { CallLogicHelper.validateParticipant(receiverId, senderId, receiverId) }

        // Non-participant throws forbidden
        val ex = assertThrows<CallForbiddenException> {
            CallLogicHelper.validateParticipant("stranger", senderId, receiverId)
        }
        assertEquals(ErrorCodes.NOT_PARTICIPANT, ex.errorCode)

        // Target receiver logic
        assertEquals(receiverId, CallLogicHelper.determineTargetReceiverId(senderId, senderId, receiverId))
        assertEquals(senderId, CallLogicHelper.determineTargetReceiverId(receiverId, senderId, receiverId))
    }

    @Test
    fun `validateExchangeActive requires ACCEPTED status`() {
        assertDoesNotThrow { CallLogicHelper.validateExchangeActive(ExchangeStatus.ACCEPTED.name) }

        val exPending = assertThrows<CallConflictException> {
            CallLogicHelper.validateExchangeActive(ExchangeStatus.PENDING.name)
        }
        assertEquals(ErrorCodes.EXCHANGE_NOT_ACTIVE, exPending.errorCode)

        val exCompleted = assertThrows<CallConflictException> {
            CallLogicHelper.validateExchangeActive(ExchangeStatus.COMPLETED.name)
        }
        assertEquals(ErrorCodes.EXCHANGE_NOT_ACTIVE, exCompleted.errorCode)
    }

    @Test
    fun `checkSpamLimit throws rate limit after threshold`() {
        assertDoesNotThrow { CallLogicHelper.checkSpamLimit(0) }
        assertDoesNotThrow { CallLogicHelper.checkSpamLimit(2) }

        val ex = assertThrows<CallRateLimitException> {
            CallLogicHelper.checkSpamLimit(3)
        }
        assertEquals(ErrorCodes.CALL_COOLDOWN_ACTIVE, ex.errorCode)
    }

    @Test
    fun `orderUserIds sorts identifiers deterministically`() {
        val (first1, second1) = CallLogicHelper.orderUserIds("alice", "bob")
        assertEquals("alice", first1)
        assertEquals("bob", second1)

        val (first2, second2) = CallLogicHelper.orderUserIds("bob", "alice")
        assertEquals("alice", first2)
        assertEquals("bob", second2)
    }

    @Test
    fun `receiver join and action validations enforce permissions and terminal guards`() {
        val receiver = "receiver-1"
        val other = "other-user"

        // validateReceiverJoin
        assertDoesNotThrow {
            CallLogicHelper.validateReceiverJoin(receiver, receiver, CallStatus.DIALING.name)
        }
        val exForbiddenJoin = assertThrows<CallForbiddenException> {
            CallLogicHelper.validateReceiverJoin(other, receiver, CallStatus.DIALING.name)
        }
        assertEquals(ErrorCodes.FORBIDDEN, exForbiddenJoin.errorCode)

        val exEndedJoin = assertThrows<CallConflictException> {
            CallLogicHelper.validateReceiverJoin(receiver, receiver, CallStatus.ENDED.name)
        }
        assertEquals(ErrorCodes.CONFLICT, exEndedJoin.errorCode)

        // validateAcceptConnected
        assertTrue(CallLogicHelper.validateAcceptConnected(receiver, receiver, CallStatus.RINGING.name))
        assertFalse(CallLogicHelper.validateAcceptConnected(receiver, receiver, CallStatus.CONNECTED.name))
        assertThrows<CallForbiddenException> {
            CallLogicHelper.validateAcceptConnected(other, receiver, CallStatus.RINGING.name)
        }
        assertThrows<CallConflictException> {
            CallLogicHelper.validateAcceptConnected(receiver, receiver, CallStatus.REJECTED.name)
        }

        // validateRejectCall
        assertTrue(CallLogicHelper.validateRejectCall(receiver, receiver, CallStatus.DIALING.name))
        assertFalse(CallLogicHelper.validateRejectCall(receiver, receiver, CallStatus.ENDED.name))
        assertThrows<CallForbiddenException> {
            CallLogicHelper.validateRejectCall(other, receiver, CallStatus.DIALING.name)
        }

        // validateEndCallParticipant
        assertDoesNotThrow { CallLogicHelper.validateEndCallParticipant("caller-1", "caller-1", receiver) }
        assertDoesNotThrow { CallLogicHelper.validateEndCallParticipant(receiver, "caller-1", receiver) }
        assertThrows<CallForbiddenException> {
            CallLogicHelper.validateEndCallParticipant(other, "caller-1", receiver)
        }
    }

    @Test
    fun `duration calculation and end status determination`() {
        val now = LocalDateTime.of(2026, 11, 4, 10, 0, 0)
        val connectedAt = now.minusSeconds(125)

        val duration = CallLogicHelper.calculateCallDuration(CallStatus.CONNECTED.name, connectedAt, now)
        assertEquals(125, duration)

        val durationNotConnected = CallLogicHelper.calculateCallDuration(CallStatus.DIALING.name, connectedAt, now)
        assertEquals(0, durationNotConnected)

        val durationNull = CallLogicHelper.calculateCallDuration(CallStatus.CONNECTED.name, null, now)
        assertEquals(0, durationNull)

        assertEquals(CallStatus.MISSED.name, CallLogicHelper.determineEndStatus(wasUnconnected = true))
        assertEquals(CallStatus.ENDED.name, CallLogicHelper.determineEndStatus(wasUnconnected = false))
    }

    @Test
    fun `rate limiting and action rate limiting tests`() {
        val timestamps = ConcurrentHashMap<String, MutableList<Long>>()
        val lastInitiated = ConcurrentHashMap<String, Long>()
        val userId = "test-user"
        val now = 100_000L

        // Normal requests under limit
        for (i in 1..4) {
            assertDoesNotThrow {
                CallLogicHelper.checkRateLimit(userId, timestamps, null, now = now + (i * 10))
            }
        }

        // 5th request succeeds
        assertDoesNotThrow {
            CallLogicHelper.checkRateLimit(userId, timestamps, null, now = now + 50)
        }

        // 6th request within window fails
        val exRate = assertThrows<CallRateLimitException> {
            CallLogicHelper.checkRateLimit(userId, timestamps, null, now = now + 60)
        }
        assertEquals(ErrorCodes.RATE_LIMITED, exRate.errorCode)

        // Cooldown check
        lastInitiated[userId] = now
        val exCooldown = assertThrows<CallRateLimitException> {
            CallLogicHelper.checkRateLimit(userId, ConcurrentHashMap(), lastInitiated, now = now + 5_000L)
        }
        assertEquals(ErrorCodes.CALL_COOLDOWN_ACTIVE, exCooldown.errorCode)

        // Action rate limit
        val actionMap = ConcurrentHashMap<String, MutableList<Long>>()
        for (i in 1..30) {
            CallLogicHelper.checkActionRateLimit(userId, actionMap, now = now + i)
        }
        val exAction = assertThrows<CallRateLimitException> {
            CallLogicHelper.checkActionRateLimit(userId, actionMap, now = now + 31)
        }
        assertEquals(ErrorCodes.RATE_LIMITED, exAction.errorCode)
    }
}