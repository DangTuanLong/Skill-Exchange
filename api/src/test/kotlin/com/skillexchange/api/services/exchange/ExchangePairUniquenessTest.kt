package com.skillexchange.api.services.exchange

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExchangePairUniquenessTest {

    @Test
    fun `exception types have correct messages`() {
        val activeEx = PairAlreadyActiveException("Cặp kỹ năng này đang có yêu cầu trao đổi hoạt động")
        assertEquals("Cặp kỹ năng này đang có yêu cầu trao đổi hoạt động", activeEx.message)

        val limitEx = PendingLimitReachedException("Đã đạt giới hạn tối đa 3 yêu cầu đang chờ phản hồi giữa hai người")
        assertEquals("Đã đạt giới hạn tối đa 3 yêu cầu đang chờ phản hồi giữa hai người", limitEx.message)
    }

    /**
     * Normalized 4-tuple calculation mirroring DEC-028:
     * (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id),
     *  CASE WHEN sender_id < receiver_id THEN skill_offered_id ELSE skill_wanted_id END,
     *  CASE WHEN sender_id < receiver_id THEN skill_wanted_id ELSE skill_offered_id END)
     */
    private data class NormalizedPair(
        val user1: String,
        val user2: String,
        val user1OfferedSkill: Int,
        val user2OfferedSkill: Int
    )

    private fun normalize(senderId: String, receiverId: String, offeredId: Int, wantedId: Int): NormalizedPair {
        return if (senderId < receiverId) {
            NormalizedPair(senderId, receiverId, offeredId, wantedId)
        } else {
            NormalizedPair(receiverId, senderId, wantedId, offeredId)
        }
    }

    @Test
    fun `symmetric pair normalization correctly identifies identical active pair regardless of initiator`() {
        val userA = "user-001"
        val userB = "user-002"
        val skillKotlin = 10
        val skillEnglish = 20

        // User A sends to B: A teaches Kotlin (10), wants English (20)
        val tupleFromA = normalize(
            senderId = userA,
            receiverId = userB,
            offeredId = skillKotlin,
            wantedId = skillEnglish
        )

        // Later (or concurrently), User B sends to A: B teaches English (20), wants Kotlin (10)
        // This is the EXACT SAME exchange proposal initiated from the other side
        val tupleFromB = normalize(
            senderId = userB,
            receiverId = userA,
            offeredId = skillEnglish,
            wantedId = skillKotlin
        )

        assertEquals(tupleFromA, tupleFromB, "Both directions must produce identical normalized 4-tuples to trigger unique index conflict")
    }

    @Test
    fun `reversed role exchange produces distinct normalized pair and does not collide`() {
        val userA = "user-001"
        val userB = "user-002"
        val skillKotlin = 10
        val skillEnglish = 20

        // Pair 1: A teaches Kotlin, B teaches English
        val pair1 = normalize(
            senderId = userA,
            receiverId = userB,
            offeredId = skillKotlin,
            wantedId = skillEnglish
        )

        // Pair 2: Roles reversed! A teaches English, B teaches Kotlin
        val pair2 = normalize(
            senderId = userA,
            receiverId = userB,
            offeredId = skillEnglish,
            wantedId = skillKotlin
        )

        // Pair 3: B initiates reversed role: B teaches Kotlin, A teaches English
        val pair3 = normalize(
            senderId = userB,
            receiverId = userA,
            offeredId = skillKotlin,
            wantedId = skillEnglish
        )

        assertTrue(pair1 != pair2, "Reversed roles must not collide with original direction")
        assertEquals(pair2, pair3, "Reverse role initiated by either A or B must normalize identically")
    }

    @Test
    fun `pending limit per pair rule - rejects when 3 pending requests already exist`() {
        val maxPending = 3

        fun checkCanSend(currentPendingCount: Int) {
            if (currentPendingCount >= maxPending) {
                throw PendingLimitReachedException("Đã đạt giới hạn tối đa $maxPending yêu cầu")
            }
        }

        // 0, 1, 2 pending -> allowed
        checkCanSend(0)
        checkCanSend(1)
        checkCanSend(2)

        // 3 pending -> throws exception
        assertFailsWith<PendingLimitReachedException> {
            checkCanSend(3)
        }

        // 4 pending -> throws exception
        assertFailsWith<PendingLimitReachedException> {
            checkCanSend(4)
        }
    }

    @Test
    fun `recentlyRejected rule - true within 14 days of rejection, false otherwise`() {
        val now = LocalDateTime.now()
        val cooldownDays = 14L

        fun isRecentlyRejected(status: String, resolvedAt: LocalDateTime): Boolean {
            val cutoff = now.minusDays(cooldownDays)
            return status == "REJECTED" && resolvedAt.isAfter(cutoff)
        }

        // Rejected 2 days ago -> true
        assertTrue(isRecentlyRejected("REJECTED", now.minusDays(2)))

        // Rejected 13 days ago -> true
        assertTrue(isRecentlyRejected("REJECTED", now.minusDays(13)))

        // Rejected 15 days ago -> false
        assertFalse(isRecentlyRejected("REJECTED", now.minusDays(15)))

        // Cancelled 2 days ago -> false (only REJECTED shows warning flag)
        assertFalse(isRecentlyRejected("CANCELLED", now.minusDays(2)))

        // Completed 2 days ago -> false
        assertFalse(isRecentlyRejected("COMPLETED", now.minusDays(2)))
    }
}

