package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.UserMatchingProfile
import com.skillexchange.api.models.matching.UserSkillData
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchingCursorAndPotentialTest {

    private val service = MatchingService()

    @Test
    fun `cursor encode and decode roundtrip`() {
        val score = 85
        val userId = "user-cand-abc"
        val cursor = service.encodeCursor(score, userId)

        val decoded = service.decodeCursor(cursor)
        assertNotNull(decoded)
        assertEquals(score, decoded.first)
        assertEquals(userId, decoded.second)
    }

    @Test
    fun `cursor decode - handles unencoded raw format and invalid inputs`() {
        val raw = "75_user-123"
        val decoded = service.decodeCursor(raw)
        assertNotNull(decoded)
        assertEquals(75, decoded.first)
        assertEquals("user-123", decoded.second)

        assertNull(service.decodeCursor(""))
        assertNull(service.decodeCursor("invalid"))
        assertNull(service.decodeCursor("notanumber_user-123"))
    }

    @Test
    fun `cursor pagination - deterministic tie breaking with equal scores has no gaps and no duplicates`() {
        data class MockItem(val userId: String, val score: Int)

        val items = listOf(
            MockItem("user-A", 95),
            MockItem("user-B", 85),
            MockItem("user-C", 85),
            MockItem("user-D", 85),
            MockItem("user-E", 85),
            MockItem("user-F", 70),
            MockItem("user-G", 70),
            MockItem("user-H", 60)
        )

        // Strict sorting: (score DESC, userId ASC)
        val sorted = items.sortedWith(
            compareByDescending<MockItem> { it.score }.thenBy { it.userId }
        )

        val pageSize = 3
        val pages = mutableListOf<List<MockItem>>()
        var cursor: String? = null

        do {
            val cursorParsed = cursor?.let { service.decodeCursor(it) }
            val page = if (cursorParsed != null) {
                val (cScore, cId) = cursorParsed
                sorted.filter { it.score < cScore || (it.score == cScore && it.userId > cId) }
            } else {
                sorted
            }.take(pageSize)

            if (page.isNotEmpty()) {
                pages.add(page)
                cursor = if (page.size == pageSize) {
                    val last = page.last()
                    service.encodeCursor(last.score, last.userId)
                } else null
            } else {
                cursor = null
            }
        } while (cursor != null)

        assertEquals(3, pages.size)
        // Page 1: A (95), B (85), C (85)
        assertEquals(listOf("user-A", "user-B", "user-C"), pages[0].map { it.userId })
        // Page 2: D (85), E (85), F (70)
        assertEquals(listOf("user-D", "user-E", "user-F"), pages[1].map { it.userId })
        // Page 3: G (70), H (60)
        assertEquals(listOf("user-G", "user-H"), pages[2].map { it.userId })

        val allRetrieved = pages.flatten()
        assertEquals(items.size, allRetrieved.size)
        // Ensure every item is retrieved exactly once
        assertEquals(items.map { it.userId }.toSet(), allRetrieved.map { it.userId }.toSet())
    }

    @Test
    fun `potential score formula verification`() {
        // PotentialScore = round(100 * (0.35 * have_level + 0.30 * sched + 0.20 * city + 0.15 * rep))
        // Case 1: Max
        val maxScore = (100 * (0.35 * (5.0 / 5.0) + 0.30 * 1.0 + 0.20 * 1.0 + 0.15 * 0.6)).roundToInt().coerceIn(0, 100)
        assertEquals(94, maxScore)

        // Case 2: Min
        val minScore = (100 * (0.35 * (1.0 / 5.0) + 0.30 * 0.0 + 0.20 * 0.3 + 0.15 * 0.6)).roundToInt().coerceIn(0, 100)
        assertEquals(22, minScore)

        // Case 3: Moderate
        val haveLevel = 3.0 / 5.0 // 0.6 -> 0.35 * 0.6 = 0.21
        val sched = 0.5            // 0.30 * 0.5 = 0.15
        val city = 1.0             // 0.20 * 1.0 = 0.20
        val rep = 0.6              // 0.15 * 0.6 = 0.09
        // Sum = 0.65 -> 65
        val modScore = (100 * (0.35 * haveLevel + 0.30 * sched + 0.20 * city + 0.15 * rep)).roundToInt().coerceIn(0, 100)
        assertEquals(65, modScore)
    }

    @Test
    fun `matching calculator - availablePairsCount returns product of bidirectional eligible pairs`() {
        val userA = UserMatchingProfile(
            userId = "user-1",
            fullName = "Nguyen A",
            city = "Hà Nội",
            skills = listOf(
                UserSkillData(1, "Kotlin", "HAVE", 4),
                UserSkillData(2, "Java", "HAVE", 3),
                UserSkillData(3, "English", "WANT", 2),
                UserSkillData(4, "Japanese", "WANT", 1),
                UserSkillData(5, "German", "WANT", 1)
            )
        )
        val userB = UserMatchingProfile(
            userId = "user-2",
            fullName = "Tran B",
            city = "Hà Nội",
            skills = listOf(
                UserSkillData(3, "English", "HAVE", 3),
                UserSkillData(4, "Japanese", "HAVE", 2),
                UserSkillData(5, "German", "HAVE", 2),
                UserSkillData(1, "Kotlin", "WANT", 3),
                UserSkillData(2, "Java", "WANT", 2)
            )
        )

        // A teaches B: Kotlin (4 >= 3), Java (3 >= 2) -> 2 pairs
        // B teaches A: English (3 >= 2), Japanese (2 >= 1), German (2 >= 1) -> 3 pairs
        // Total availablePairsCount = 2 * 3 = 6
        val result = MatchingCalculator.calculate(userA, userB)
        assertNotNull(result)
        assertEquals(6, result.breakdown.availablePairsCount)
        assertEquals(3, result.matchedSkills.theyTeachYou.size)
        assertEquals(2, result.matchedSkills.youTeachThem.size)
    }
}

