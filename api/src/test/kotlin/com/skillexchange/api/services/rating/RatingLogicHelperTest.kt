package com.skillexchange.api.services.rating

import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.rating.CreateRatingRequest
import com.skillexchange.api.plugins.ValidationException
import java.util.UUID
import kotlin.test.*

class RatingLogicHelperTest {

    private inline fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            fail("Expected no exception but threw: ${e.message}")
        }
    }

    @Test
    fun `validateCreateRatingInput trims comment and validates score bounds`() {
        // Valid inputs
        val (score1, comment1) = RatingLogicHelper.validateCreateRatingInput(
            CreateRatingRequest(exchangeId = "e1", score = 5, comment = "  Rất tốt!  ")
        )
        assertEquals(5, score1)
        assertEquals("Rất tốt!", comment1)

        val (score2, comment2) = RatingLogicHelper.validateCreateRatingInput(
            CreateRatingRequest(exchangeId = "e1", score = 1, comment = "   ")
        )
        assertEquals(1, score2)
        assertNull(comment2)

        // Invalid score
        assertFailsWith<ValidationException> {
            RatingLogicHelper.validateCreateRatingInput(CreateRatingRequest(exchangeId = "e1", score = 0))
        }
        assertFailsWith<ValidationException> {
            RatingLogicHelper.validateCreateRatingInput(CreateRatingRequest(exchangeId = "e1", score = 6))
        }

        // Invalid comment (> 200 chars)
        val longComment = "a".repeat(201)
        assertFailsWith<ValidationException> {
            RatingLogicHelper.validateCreateRatingInput(
                CreateRatingRequest(exchangeId = "e1", score = 4, comment = longComment)
            )
        }
    }

    @Test
    fun `parseExchangeUuid validates UUID string`() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, RatingLogicHelper.parseExchangeUuid(uuid.toString()))

        assertFailsWith<RatingNotFoundException> {
            RatingLogicHelper.parseExchangeUuid("not-a-uuid")
        }
    }

    @Test
    fun `validateRatingEligibility checks membership status and idempotency`() {
        val sender = "user-sender"
        val receiver = "user-receiver"

        // Caller is sender -> reviewee is receiver
        val reviewee1 = RatingLogicHelper.validateRatingEligibility(
            callerUserId = sender,
            senderId = sender,
            receiverId = receiver,
            currentStatus = ExchangeStatus.COMPLETED.name,
            hasExistingRating = false
        )
        assertEquals(receiver, reviewee1)

        // Caller is receiver -> reviewee is sender
        val reviewee2 = RatingLogicHelper.validateRatingEligibility(
            callerUserId = receiver,
            senderId = sender,
            receiverId = receiver,
            currentStatus = ExchangeStatus.COMPLETED.name,
            hasExistingRating = false
        )
        assertEquals(sender, reviewee2)

        // Non-participant
        assertFailsWith<RatingForbiddenException> {
            RatingLogicHelper.validateRatingEligibility(
                callerUserId = "third-party",
                senderId = sender,
                receiverId = receiver,
                currentStatus = ExchangeStatus.COMPLETED.name,
                hasExistingRating = false
            )
        }

        // Status not COMPLETED
        assertFailsWith<ValidationException> {
            RatingLogicHelper.validateRatingEligibility(
                callerUserId = sender,
                senderId = sender,
                receiverId = receiver,
                currentStatus = ExchangeStatus.ACCEPTED.name,
                hasExistingRating = false
            )
        }

        // Already rated
        assertFailsWith<RatingConflictException> {
            RatingLogicHelper.validateRatingEligibility(
                callerUserId = sender,
                senderId = sender,
                receiverId = receiver,
                currentStatus = ExchangeStatus.COMPLETED.name,
                hasExistingRating = true
            )
        }
    }

    @Test
    fun `validateExchangeMember enforces member access`() {
        assertDoesNotThrow { RatingLogicHelper.validateExchangeMember("u1", "u1", "u2") }
        assertDoesNotThrow { RatingLogicHelper.validateExchangeMember("u2", "u1", "u2") }
        assertFailsWith<RatingForbiddenException> {
            RatingLogicHelper.validateExchangeMember("u3", "u1", "u2")
        }
    }
}

