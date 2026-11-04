package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.UserMatchingProfile
import com.skillexchange.api.models.matching.UserSkillData
import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlin.test.*

class MatchingLogicHelperTest {

    @Test
    fun `cursor encoding and decoding works with base64 and fallback`() {
        val encoded = MatchingLogicHelper.encodeCursor(92, "user-abc-123")
        assertNotNull(encoded)
        assertTrue(encoded.isNotBlank())

        val decoded = MatchingLogicHelper.decodeCursor(encoded)
        assertNotNull(decoded)
        assertEquals(92, decoded!!.first)
        assertEquals("user-abc-123", decoded.second)

        // Raw fallback support "score_userId"
        val decodedRaw = MatchingLogicHelper.decodeCursor("75_user-raw")
        assertNotNull(decodedRaw)
        assertEquals(75, decodedRaw!!.first)
        assertEquals("user-raw", decodedRaw.second)

        // Invalid cursor inputs
        assertNull(MatchingLogicHelper.decodeCursor(""))
        assertNull(MatchingLogicHelper.decodeCursor("   "))
        assertNull(MatchingLogicHelper.decodeCursor("not_a_valid_score_user"))
        assertNull(MatchingLogicHelper.decodeCursor("invalid"))
    }

    @Test
    fun `calculatePotentialScore adheres to formula and clamping`() {
        // level 5 (1.0), sched 1.0, city 1.0, rep 0.6:
        // raw = 0.35*1.0 + 0.30*1.0 + 0.20*1.0 + 0.15*0.6 = 0.35 + 0.30 + 0.20 + 0.09 = 0.94 -> 94
        val scoreMax = MatchingLogicHelper.calculatePotentialScore(5, 1.0, 1.0, 0.6)
        assertEquals(94, scoreMax)

        // level 1 (0.2), sched 0.0, city 0.0, rep 0.6:
        // raw = 0.35*0.2 + 0.0 + 0.0 + 0.09 = 0.07 + 0.09 = 0.16 -> 16
        val scoreMin = MatchingLogicHelper.calculatePotentialScore(1, 0.0, 0.0, 0.6)
        assertEquals(16, scoreMin)
    }

    @Test
    fun `rankSuggestions sorts deterministically and honors cursor and limit`() {
        val caller = UserMatchingProfile(
            userId = "caller",
            fullName = "Caller User",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "HAVE", 4),
                UserSkillData(2, "Python", "WANT", 3)
            )
        )

        // Candidate 1: matches Kotlin (wants) & Python (has) -> 2-way match
        val cand1 = UserMatchingProfile(
            userId = "cand-1",
            fullName = "Candidate 1",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "WANT", 3),
                UserSkillData(2, "Python", "HAVE", 4)
            )
        )

        // Candidate 2: also 2-way match but different city -> slightly lower score
        val cand2 = UserMatchingProfile(
            userId = "cand-2",
            fullName = "Candidate 2",
            avatarUrl = null,
            city = "Đà Nẵng",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "WANT", 3),
                UserSkillData(2, "Python", "HAVE", 4)
            )
        )

        val ranked = MatchingLogicHelper.rankSuggestions(caller, listOf(cand2, cand1), limit = 10)
        assertEquals(2, ranked.size)
        // cand1 has same city -> higher score -> ranked first
        assertEquals("cand-1", ranked[0].userId)
        assertEquals("cand-2", ranked[1].userId)
        assertTrue(ranked[0].percentage >= ranked[1].percentage)

        // Test cursor pagination: skip cand1
        val cursorAfterCand1 = MatchingLogicHelper.encodeCursor(ranked[0].percentage, ranked[0].userId)
        val paged = MatchingLogicHelper.rankSuggestions(caller, listOf(cand2, cand1), cursor = cursorAfterCand1, limit = 10)
        assertEquals(1, paged.size)
        assertEquals("cand-2", paged[0].userId)
    }

    @Test
    fun `rankPotentialMatches excludes 2-way matches and non-learnable skills`() {
        val caller = UserMatchingProfile(
            userId = "caller",
            fullName = "Caller User",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "HAVE", 4),
                UserSkillData(2, "Python", "WANT", 3)
            )
        )

        // Candidate 1: 1-way match. Candidate wants Kotlin (level 3, caller has 4), has Rust (level 4, caller doesn't have)
        val cand1 = UserMatchingProfile(
            userId = "cand-rust",
            fullName = "Rust Ace",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "WANT", 3),
                UserSkillData(3, "Rust", "HAVE", 4)
            )
        )

        // Candidate 2: 2-way match. Should be excluded from potential matches!
        val cand2TwoWay = UserMatchingProfile(
            userId = "cand-twoway",
            fullName = "Two Way",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "WANT", 3),
                UserSkillData(2, "Python", "HAVE", 4)
            )
        )

        // Candidate 3: wants Kotlin at level 5 (caller only has level 4) -> cannot teach -> excluded!
        val cand3TooAdvanced = UserMatchingProfile(
            userId = "cand-advanced",
            fullName = "Kotlin Master",
            avatarUrl = null,
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto("MON", "08:00", "12:00")),
            skills = listOf(
                UserSkillData(1, "Kotlin", "WANT", 5),
                UserSkillData(4, "Go", "HAVE", 4)
            )
        )

        val potentials = MatchingLogicHelper.rankPotentialMatches(caller, listOf(cand1, cand2TwoWay, cand3TooAdvanced))
        assertEquals(1, potentials.size)
        assertEquals("cand-rust", potentials[0].userId)
        assertEquals(1, potentials[0].canTeachSkill.skillId)
        assertEquals(3, potentials[0].suggestedWantSkill.skillId)
        assertTrue(potentials[0].potentialScore > 0)

        // When caller has no HAVE skills, returns empty
        val callerNoHave = caller.copy(skills = listOf(UserSkillData(2, "Python", "WANT", 3)))
        assertTrue(MatchingLogicHelper.rankPotentialMatches(callerNoHave, listOf(cand1)).isEmpty())
    }
}
