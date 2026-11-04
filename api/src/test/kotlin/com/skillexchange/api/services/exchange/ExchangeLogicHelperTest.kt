package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.ValidationException
import java.time.LocalDateTime
import kotlin.test.*

class ExchangeLogicHelperTest {

    private val futureTimeStr = LocalDateTime.now().plusDays(2).toString()
    private val pastTimeStr = LocalDateTime.now().minusDays(1).toString()

    @Test
    fun `validateCreateExchangeInput accepts valid inputs`() {
        val validReq = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "online",
            message = "Trao đổi kỹ năng nhé!",
            scheduledAt = futureTimeStr
        )
        // Should not throw
        ExchangeLogicHelper.validateCreateExchangeInput("user-alice", validReq)
    }

    @Test
    fun `validateCreateExchangeInput rejects self exchange`() {
        val req = CreateExchangeRequest(
            receiverId = "user-alice",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            message = null,
            scheduledAt = futureTimeStr
        )
        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateCreateExchangeInput("user-alice", req)
        }
        assertTrue(ex.message!!.contains("chính mình"))
    }

    @Test
    fun `validateCreateExchangeInput rejects message exceeding max length`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 60,
            meetingMode = "ONLINE",
            message = "a".repeat(1001),
            scheduledAt = futureTimeStr
        )
        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateCreateExchangeInput("user-alice", req)
        }
        assertTrue(ex.message!!.contains("1000"))
    }

    @Test
    fun `validateCreateExchangeInput rejects invalid durationMinutes`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 45,
            meetingMode = "ONLINE",
            message = "Hello",
            scheduledAt = futureTimeStr
        )
        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateCreateExchangeInput("user-alice", req)
        }
        assertTrue(ex.message!!.contains("30, 60, 90 hoặc 120"))
    }

    @Test
    fun `validateCreateExchangeInput rejects invalid meetingMode`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 30,
            meetingMode = "ZOOM_MEETING",
            message = null,
            scheduledAt = futureTimeStr
        )
        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateCreateExchangeInput("user-alice", req)
        }
        assertTrue(ex.message!!.contains("Chế độ gặp gỡ không hợp lệ"))
    }

    @Test
    fun `validateCreateExchangeInput rejects past scheduledAt`() {
        val req = CreateExchangeRequest(
            receiverId = "user-bob",
            skillOfferedId = 1,
            skillWantedId = 2,
            durationMinutes = 90,
            meetingMode = "IN_PERSON",
            message = null,
            scheduledAt = pastTimeStr
        )
        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateCreateExchangeInput("user-alice", req)
        }
        assertTrue(ex.message!!.contains("ở tương lai"))
    }

    @Test
    fun `parseIsoDateTime parses various formats and rejects invalid`() {
        assertNotNull(ExchangeLogicHelper.parseIsoDateTime("2026-11-10T14:30:00"))
        assertNotNull(ExchangeLogicHelper.parseIsoDateTime("2026-11-10T14:30:00+07:00"))
        assertNotNull(ExchangeLogicHelper.parseIsoDateTime("2026-11-10T14:30:00Z"))

        val ex = assertFailsWith<ValidationException> {
            ExchangeLogicHelper.parseIsoDateTime("not-a-date")
        }
        assertTrue(ex.message!!.contains("Định dạng scheduled_at không hợp lệ"))
    }

    @Test
    fun `validateProficiencyMatch validates bidirectional competency`() {
        // Success: sender has 4 >= 2, receiver has 3 >= 3
        ExchangeLogicHelper.validateProficiencyMatch(
            senderHaveLevel = 4,
            receiverWantLevel = 2,
            receiverHaveLevel = 3,
            senderWantLevel = 3
        )

        // Sender missing offered skill
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(null, 2, 3, 3)
        }
        // Receiver missing want
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(4, null, 3, 3)
        }
        // Sender level too low
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(1, 3, 3, 2)
        }
        // Receiver missing have
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(4, 2, null, 3)
        }
        // Sender missing want
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(4, 2, 3, null)
        }
        // Receiver level too low
        assertFailsWith<ValidationException> {
            ExchangeLogicHelper.validateProficiencyMatch(4, 2, 1, 3)
        }
    }

    @Test
    fun `checkSymmetricPairActive detects active in both directions`() {
        val existing = listOf(
            ExistingExchangeRequestSummary(
                senderId = "user-alice",
                receiverId = "user-bob",
                skillOfferedId = 10,
                skillWantedId = 20,
                status = ExchangeStatus.PENDING.name
            ),
            ExistingExchangeRequestSummary(
                senderId = "user-bob",
                receiverId = "user-charlie",
                skillOfferedId = 30,
                skillWantedId = 40,
                status = ExchangeStatus.ACCEPTED.name
            )
        )

        // Same direction active
        assertTrue(ExchangeLogicHelper.checkSymmetricPairActive("user-alice", "user-bob", 10, 20, existing))

        // Inverted direction active (Bob offered 20, wanted 10 to Alice)
        assertTrue(ExchangeLogicHelper.checkSymmetricPairActive("user-bob", "user-alice", 20, 10, existing))

        // Different skills not active
        assertFalse(ExchangeLogicHelper.checkSymmetricPairActive("user-alice", "user-bob", 10, 99, existing))

        // Different participants not active
        assertFalse(ExchangeLogicHelper.checkSymmetricPairActive("user-alice", "user-charlie", 10, 20, existing))
    }

    @Test
    fun `checkPendingLimitReached counts pending in both directions`() {
        val existing = listOf(
            ExistingExchangeRequestSummary(senderId = "A", receiverId = "B", skillOfferedId = 1, skillWantedId = 2, status = "PENDING"),
            ExistingExchangeRequestSummary(senderId = "B", receiverId = "A", skillOfferedId = 3, skillWantedId = 4, status = "PENDING"),
            ExistingExchangeRequestSummary(senderId = "A", receiverId = "B", skillOfferedId = 5, skillWantedId = 6, status = "COMPLETED")
        )

        // Current count is 2 -> not reached 3
        assertFalse(ExchangeLogicHelper.checkPendingLimitReached("A", "B", existing, maxLimit = 3))

        val with3Pending = existing + ExistingExchangeRequestSummary(
            senderId = "A", receiverId = "B", skillOfferedId = 7, skillWantedId = 8, status = "PENDING"
        )
        // Now count is 3 -> limit reached!
        assertTrue(ExchangeLogicHelper.checkPendingLimitReached("A", "B", with3Pending, maxLimit = 3))
        // Symmetric check from B's perspective is also reached
        assertTrue(ExchangeLogicHelper.checkPendingLimitReached("B", "A", with3Pending, maxLimit = 3))
    }

    @Test
    fun `computeValidPairs correctly filters active pairs, counts completed, and detects relationship`() {
        val now = LocalDateTime.now()

        val callerHave = listOf(
            Triple(1, "Kotlin", 4),
            Triple(2, "Java", 2) // Level 2 < target 3 -> cannot teach
        )
        val receiverWant = mapOf(
            1 to Pair("Kotlin", 2),
            2 to Pair("Java", 3)
        )

        val receiverHave = listOf(
            Triple(10, "Figma", 3),
            Triple(20, "Guitar", 4)
        )
        val callerWant = mapOf(
            10 to Pair("Figma", 2),
            20 to Pair("Guitar", 1)
        )

        // Valid combinations:
        // Teach: Kotlin (1) [4 >= 2]
        // Learn: Figma (10) [3 >= 2], Guitar (20) [4 >= 1]
        // Pairs: (1, 10) and (1, 20)

        val existing = listOf(
            // Pair (1, 10) is active PENDING
            ExistingExchangeRequestSummary(
                senderId = "alice",
                receiverId = "bob",
                skillOfferedId = 1,
                skillWantedId = 10,
                status = ExchangeStatus.PENDING.name
            ),
            // Pair (1, 20) has 2 completed sessions
            ExistingExchangeRequestSummary(
                senderId = "alice",
                receiverId = "bob",
                skillOfferedId = 1,
                skillWantedId = 20,
                status = ExchangeStatus.COMPLETED.name
            ),
            ExistingExchangeRequestSummary(
                senderId = "bob",
                receiverId = "alice",
                skillOfferedId = 20,
                skillWantedId = 1,
                status = ExchangeStatus.COMPLETED.name
            ),
            // A rejected request 5 days ago -> recentlyRejected = true
            ExistingExchangeRequestSummary(
                senderId = "bob",
                receiverId = "alice",
                skillOfferedId = 99,
                skillWantedId = 99,
                status = ExchangeStatus.REJECTED.name,
                resolvedAt = now.minusDays(5)
            )
        )

        val result = ExchangeLogicHelper.computeValidPairs(
            callerId = "alice",
            receiverId = "bob",
            callerHaveSkills = callerHave,
            receiverWantSkills = receiverWant,
            receiverHaveSkills = receiverHave,
            callerWantSkills = callerWant,
            existingRequests = existing,
            now = now,
            cooldownDays = 14
        )

        assertEquals(2, result.pairs.size)

        val pair1 = result.pairs.first { it.skillOfferedId == 1 && it.skillWantedId == 10 }
        assertTrue(pair1.isActive, "Pair (1, 10) should be marked isActive because of PENDING request")
        assertEquals(0, pair1.completedCount)

        val pair2 = result.pairs.first { it.skillOfferedId == 1 && it.skillWantedId == 20 }
        assertFalse(pair2.isActive, "Pair (1, 20) is not active")
        assertEquals(2, pair2.completedCount, "Pair (1, 20) was completed twice across both directions")

        assertTrue(result.recentlyRejected)
        assertEquals("ACTIVE", result.relationshipStatus)
        assertEquals(2, result.completedExchangeCount)
    }

    @Test
    fun `computeValidPairs returns empty for same user or no matching skills`() {
        val resultSameUser = ExchangeLogicHelper.computeValidPairs(
            callerId = "alice",
            receiverId = "alice",
            callerHaveSkills = emptyList(),
            receiverWantSkills = emptyMap(),
            receiverHaveSkills = emptyList(),
            callerWantSkills = emptyMap(),
            existingRequests = emptyList()
        )
        assertTrue(resultSameUser.pairs.isEmpty())
        assertFalse(resultSameUser.recentlyRejected)
        assertNull(resultSameUser.relationshipStatus)
    }
}

