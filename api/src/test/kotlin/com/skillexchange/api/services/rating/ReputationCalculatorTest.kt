package com.skillexchange.api.services.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReputationCalculatorTest {

    @Test
    fun `calculate - When ratings are empty, returns null score and New Member badge`() {
        val result = ReputationCalculator.calculate(
            ratings = emptyList(),
            completedCount = 0,
            cancelledAfterAcceptedCount = 0
        )

        assertNull(result.score)
        assertNull(result.avgRating)
        assertEquals(ReputationCalculator.BADGE_NEW_MEMBER, result.badge)
        assertEquals(0, result.ratingCount)
        assertEquals(0.0, result.completionRate)
        assertEquals(0, result.totalExchanges)
    }

    @Test
    fun `calculate - Division by zero when completed and cancelled are both 0 yields completionRate 0`() {
        val result = ReputationCalculator.calculate(
            ratings = listOf(5),
            completedCount = 0,
            cancelledAfterAcceptedCount = 0
        )

        assertEquals(0.0, result.completionRate)
        // sessions = 0 < 3 -> New Member
        assertEquals(ReputationCalculator.BADGE_NEW_MEMBER, result.badge)
    }

    @Test
    fun `calculate - sessions fewer than 3 returns New Member badge regardless of high score`() {
        // sessions = 2 < 3
        val result = ReputationCalculator.calculate(
            ratings = listOf(5, 5, 5),
            completedCount = 2,
            cancelledAfterAcceptedCount = 0
        )

        assertEquals(ReputationCalculator.BADGE_NEW_MEMBER, result.badge)
    }

    @Test
    fun `calculate - score less than 2 returns New Member badge even if sessions are large`() {
        // avg = 1.0, completionRate = 1.0, activityBonus = 10/20 = 0.5
        // score = 1.0 * 0.6 + 5.0 * 0.3 + 2.5 * 0.1 = 0.6 + 1.5 + 0.25 = 2.35
        // To get score < 2.0: high cancelledAfterAccepted
        // e.g. completed = 3, cancelled = 27 -> completionRate = 3/30 = 0.1
        // avg = 1.0 -> 1.0 * 0.6 + (0.1 * 5) * 0.3 + (3/20 * 5) * 0.1 = 0.6 + 0.15 + 0.075 = 0.825 -> score = 0.8 < 2.0
        val result = ReputationCalculator.calculate(
            ratings = listOf(1, 1, 1),
            completedCount = 3,
            cancelledAfterAcceptedCount = 27
        )

        assertEquals(ReputationCalculator.BADGE_NEW_MEMBER, result.badge)
    }

    @Test
    fun `calculate - Gap condition returns null badge when score is 2 to 3_5 or sessions 3 to 4 with high score`() {
        // Case A: score in 2.0..3.49 with sessions >= 3
        // avg = 2.5, completed = 5, cancelled = 5 -> completionRate = 0.5, bonus = 5/20 = 0.25
        // score = 2.5*0.6 + 2.5*0.3 + 1.25*0.1 = 1.5 + 0.75 + 0.125 = 2.375 -> 2.4
        val resultA = ReputationCalculator.calculate(
            ratings = listOf(2, 3),
            completedCount = 5,
            cancelledAfterAcceptedCount = 5
        )
        assertNull(resultA.badge, "Score 2.4 with 5 sessions should fall in gap and have null badge")

        // Case B: score >= 3.5 but sessions = 4 (fewer than 5)
        // avg = 5.0, completed = 4, cancelled = 0 -> completionRate = 1.0, bonus = 4/20 = 0.2
        // score = 5.0*0.6 + 5.0*0.3 + 1.0*0.1 = 3.0 + 1.5 + 0.1 = 4.6
        // score >= 3.5 but sessions = 4 (< 5 for Trusted, < 15 for Excellent)
        val resultB = ReputationCalculator.calculate(
            ratings = listOf(5, 5, 5),
            completedCount = 4,
            cancelledAfterAcceptedCount = 0
        )
        assertNull(resultB.badge, "Score >= 3.5 but only 4 sessions should have null badge")
    }

    @Test
    fun `calculate - Trusted badge when score at least 3_5 and sessions at least 5`() {
        // avg = 4.0, completed = 6, cancelled = 0 -> completionRate = 1.0, bonus = 6/20 = 0.3
        // score = 4.0*0.6 + 5.0*0.3 + 1.5*0.1 = 2.4 + 1.5 + 0.15 = 4.05 -> 4.1
        // score = 4.1 >= 3.5, sessions = 6 >= 5 (and < 15) -> Trusted
        val result = ReputationCalculator.calculate(
            ratings = listOf(4, 4, 4),
            completedCount = 6,
            cancelledAfterAcceptedCount = 0
        )

        assertEquals(ReputationCalculator.BADGE_TRUSTED, result.badge)
    }

    @Test
    fun `calculate - Excellent badge when score at least 4_5 and sessions at least 15`() {
        // avg = 5.0, completed = 20, cancelled = 0 -> completionRate = 1.0, bonus = 1.0
        // score = 5.0*0.6 + 5.0*0.3 + 5.0*0.1 = 3.0 + 1.5 + 0.5 = 5.0
        // score = 5.0 >= 4.5, sessions = 20 >= 15 -> Excellent
        val result = ReputationCalculator.calculate(
            ratings = listOf(5, 5, 5, 5),
            completedCount = 20,
            cancelledAfterAcceptedCount = 0
        )

        assertEquals(ReputationCalculator.BADGE_EXCELLENT, result.badge)
        assertEquals(5.0, result.score)
    }

    @Test
    fun `calculate - Trusted wins when score at least 4_5 but sessions between 5 and 14`() {
        // avg = 5.0, completed = 14, cancelled = 0 -> completionRate = 1.0, bonus = 14/20 = 0.7
        // score = 5.0*0.6 + 5.0*0.3 + 3.5*0.1 = 3.0 + 1.5 + 0.35 = 4.85 -> 4.9
        // score = 4.9 >= 4.5, but sessions = 14 < 15 -> Not Excellent, but matches Trusted (>= 3.5 and >= 5)
        val result = ReputationCalculator.calculate(
            ratings = listOf(5, 5),
            completedCount = 14,
            cancelledAfterAcceptedCount = 0
        )

        assertEquals(ReputationCalculator.BADGE_TRUSTED, result.badge)
    }

    @Test
    fun `calculate - Correctness of CompletionRate and ActivityBonus`() {
        // completed = 8, cancelledAfterAccepted = 2 -> completionRate = 8/10 = 0.8
        // bonus = min(1.0, 8/20 = 0.4) -> 0.4
        // avg = 4.0
        // rawScore = 4.0*0.6 + (0.8*5)*0.3 + (0.4*5)*0.1 = 2.4 + 1.2 + 0.2 = 3.8
        val result = ReputationCalculator.calculate(
            ratings = listOf(4, 4),
            completedCount = 8,
            cancelledAfterAcceptedCount = 2
        )

        assertEquals(0.8, result.completionRate)
        assertEquals(3.8, result.score)
        assertEquals(ReputationCalculator.BADGE_TRUSTED, result.badge)
    }
}
