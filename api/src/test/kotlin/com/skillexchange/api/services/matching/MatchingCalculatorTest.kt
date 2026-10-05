package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.UserMatchingProfile
import com.skillexchange.api.models.matching.UserSkillData
import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MatchingCalculatorTest {

    // ─────────────────────────────────────────────────────────────
    // 1. Tests for ProficiencyFit & boundary values of d
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculateProficiencyFit - d less than 0 is 0_0 (teacher lower than learner)`() {
        // Teacher level 2, Learner wants level 4 -> d = -2 < 0 -> 0.0
        val fit = MatchingCalculator.calculateProficiencyFit(teacherLevel = 2, learnerLevel = 4)
        assertEquals(0.0, fit)
    }

    @Test
    fun `calculateProficiencyFit - d in 0 to 2 is 1_0 (ideal fit)`() {
        // d = 0: teacher 3, learner 3 -> 1.0
        assertEquals(1.0, MatchingCalculator.calculateProficiencyFit(teacherLevel = 3, learnerLevel = 3))
        // d = 1: teacher 4, learner 3 -> 1.0
        assertEquals(1.0, MatchingCalculator.calculateProficiencyFit(teacherLevel = 4, learnerLevel = 3))
        // d = 2: teacher 5, learner 3 -> 1.0
        assertEquals(1.0, MatchingCalculator.calculateProficiencyFit(teacherLevel = 5, learnerLevel = 3))
    }

    @Test
    fun `calculateProficiencyFit - d == 3 is 0_7 (large gap)`() {
        // d = 3: teacher 5, learner 2 -> 0.7
        val fit = MatchingCalculator.calculateProficiencyFit(teacherLevel = 5, learnerLevel = 2)
        assertEquals(0.7, fit)
    }

    @Test
    fun `calculateProficiencyFit - d == 4 is 0_5 (very large gap)`() {
        // d = 4: teacher 5, learner 1 -> 0.5
        val fit = MatchingCalculator.calculateProficiencyFit(teacherLevel = 5, learnerLevel = 1)
        assertEquals(0.5, fit)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Tests for LocationBonus
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculateLocationBonus - same city yields 1_0`() {
        assertEquals(1.0, MatchingCalculator.calculateLocationBonus("Hà Nội", "Hà Nội"))
        // Case-insensitive & trimmed
        assertEquals(1.0, MatchingCalculator.calculateLocationBonus("  Đà Nẵng ", "đà nẵng"))
    }

    @Test
    fun `calculateLocationBonus - different cities yields 0_3`() {
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus("Hà Nội", "TP. Hồ Chí Minh"))
    }

    @Test
    fun `calculateLocationBonus - missing or empty city yields 0_3`() {
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus(null, "Hà Nội"))
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus("Hà Nội", null))
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus("", "Hà Nội"))
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus("Hà Nội", "  "))
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus(null, null))
        assertEquals(0.3, MatchingCalculator.calculateLocationBonus("", ""))
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Tests for AvailabilityOverlap
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculateAvailabilityOverlap - empty availability yields 0_0`() {
        val window = listOf(AvailabilityWindowDto(day = "MON", from = "18:00", to = "20:00"))
        assertEquals(0.0, MatchingCalculator.calculateAvailabilityOverlap(emptyList(), window))
        assertEquals(0.0, MatchingCalculator.calculateAvailabilityOverlap(window, emptyList()))
        assertEquals(0.0, MatchingCalculator.calculateAvailabilityOverlap(emptyList(), emptyList()))
    }

    @Test
    fun `calculateAvailabilityOverlap - partial overlap on same day`() {
        // A: MON 18:00..20:00 (120 mins)
        // B: MON 19:00..21:00 (120 mins)
        // Min total = 120 mins
        // Overlap = MON 19:00..20:00 (60 mins)
        // Overlap ratio = 60 / 120 = 0.5
        val availA = listOf(AvailabilityWindowDto(day = "MON", from = "18:00", to = "20:00"))
        val availB = listOf(AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00"))

        val ratio = MatchingCalculator.calculateAvailabilityOverlap(availA, availB)
        assertEquals(0.5, ratio)
    }

    @Test
    fun `calculateAvailabilityOverlap - disjoint windows yield 0_0`() {
        val availA = listOf(AvailabilityWindowDto(day = "MON", from = "08:00", to = "10:00"))
        val availB = listOf(AvailabilityWindowDto(day = "MON", from = "14:00", to = "16:00"))
        assertEquals(0.0, MatchingCalculator.calculateAvailabilityOverlap(availA, availB))

        val availC = listOf(AvailabilityWindowDto(day = "TUE", from = "08:00", to = "10:00"))
        assertEquals(0.0, MatchingCalculator.calculateAvailabilityOverlap(availA, availC))
    }

    @Test
    fun `calculateAvailabilityOverlap - full overlap of smaller total hours yields 1_0`() {
        // A: MON 18:00..20:00 (120m), WED 18:00..20:00 (120m). Total A = 240 mins.
        // B: MON 18:00..20:00 (120m). Total B = 120 mins.
        // Min total = 120 mins.
        // Overlap = 120 mins.
        // Ratio = 120 / 120 = 1.0.
        val availA = listOf(
            AvailabilityWindowDto(day = "MON", from = "18:00", to = "20:00"),
            AvailabilityWindowDto(day = "WED", from = "18:00", to = "20:00")
        )
        val availB = listOf(
            AvailabilityWindowDto(day = "MON", from = "18:00", to = "20:00")
        )

        val ratio = MatchingCalculator.calculateAvailabilityOverlap(availA, availB)
        assertEquals(1.0, ratio)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Full Matching Calculation with Hand-Calculated Examples
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `calculate - Scenario 1 Ideal 2-way match with full overlap and same city`() {
        // User A:
        //   HAVE: Kotlin (id=1, level=4)
        //   WANT: English (id=2, level=2)
        //   City: "Hà Nội"
        //   Avail: MON 18:00-21:00 (180 mins)
        val userA = UserMatchingProfile(
            userId = "user-a",
            fullName = "Nguyễn Văn A",
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto(day = "MON", from = "18:00", to = "21:00")),
            skills = listOf(
                UserSkillData(skillId = 1, skillName = "Kotlin", type = "HAVE", proficiencyLevel = 4),
                UserSkillData(skillId = 2, skillName = "English", type = "WANT", proficiencyLevel = 2)
            )
        )

        // User B:
        //   HAVE: English (id=2, level=3)
        //   WANT: Kotlin (id=1, level=3)
        //   City: "Hà Nội"
        //   Avail: MON 18:00-21:00 (180 mins)
        val userB = UserMatchingProfile(
            userId = "user-b",
            fullName = "Trần Thị B",
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto(day = "MON", from = "18:00", to = "21:00")),
            skills = listOf(
                UserSkillData(skillId = 2, skillName = "English", type = "HAVE", proficiencyLevel = 3),
                UserSkillData(skillId = 1, skillName = "Kotlin", type = "WANT", proficiencyLevel = 3)
            )
        )

        // Hand calculation:
        // 1. SkillMatch:
        //    - A->B (B teaches English): B.have(3) >= A.want(2) -> eligible! (+0.5)
        //    - B->A (A teaches Kotlin): A.have(4) >= B.want(3) -> eligible! (+0.5)
        //    => SkillMatch = 1.0
        //
        // 2. ProficiencyFit:
        //    - A->B: d = 3 - 2 = 1 -> fit = 1.0
        //    - B->A: d = 4 - 3 = 1 -> fit = 1.0
        //    => ProficiencyFit = (1.0 + 1.0) / 2 = 1.0
        //
        // 3. LocationBonus: "Hà Nội" == "Hà Nội" -> 1.0
        //
        // 4. AvailabilityOverlap: 180 / min(180, 180) = 1.0
        //
        // Total = 0.40 * 1.0 + 0.25 * 1.0 + 0.15 * 1.0 + 0.20 * 1.0 = 1.0000
        // Percentage = 100%

        val result = MatchingCalculator.calculate(userA, userB)
        assertNotNull(result)
        assertEquals("user-b", result.targetUserId)
        assertEquals(1.0, result.totalScore)
        assertEquals(100, result.percentage)

        assertEquals(1.0, result.breakdown.skillMatch)
        assertEquals(1.0, result.breakdown.proficiencyFit)
        assertEquals(1.0, result.breakdown.locationBonus)
        assertEquals(1.0, result.breakdown.availabilityOverlap)

        assertEquals(1, result.matchedSkills.theyTeachYou.size)
        assertEquals(1, result.matchedSkills.youTeachThem.size)
    }

    @Test
    fun `calculate - Scenario 2 1-way match with gap d=3, different city, partial availability`() {
        // User A:
        //   HAVE: Python (id=1, level=5)
        //   WANT: Piano (id=2, level=3)
        //   City: "Hà Nội"
        //   Avail: MON 18:00-20:00 (120 mins)
        val userA = UserMatchingProfile(
            userId = "user-a",
            fullName = "Lê Văn C",
            city = "Hà Nội",
            availability = listOf(AvailabilityWindowDto(day = "MON", from = "18:00", to = "20:00")),
            skills = listOf(
                UserSkillData(skillId = 1, skillName = "Python", type = "HAVE", proficiencyLevel = 5),
                UserSkillData(skillId = 2, skillName = "Piano", type = "WANT", proficiencyLevel = 3)
            )
        )

        // User B:
        //   HAVE: Guitar (id=3, level=4)  [A wants piano, not guitar -> no match for A->B]
        //   WANT: Python (id=1, level=2)
        //   City: "Đà Nẵng"
        //   Avail: MON 19:00-21:00 (120 mins)
        val userB = UserMatchingProfile(
            userId = "user-b",
            fullName = "Phạm Thị D",
            city = "Đà Nẵng",
            availability = listOf(AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00")),
            skills = listOf(
                UserSkillData(skillId = 3, skillName = "Guitar", type = "HAVE", proficiencyLevel = 4),
                UserSkillData(skillId = 1, skillName = "Python", type = "WANT", proficiencyLevel = 2)
            )
        )

        // Hand calculation:
        // 1. SkillMatch:
        //    - A->B: 0 eligible (B has Guitar, A wants Piano)
        //    - B->A: 1 eligible (A has Python 5 >= B wants Python 2) -> +0.5
        //    => SkillMatch = 0.5
        //
        // 2. ProficiencyFit:
        //    - Only B->A exists: d = 5 - 2 = 3 -> fit = 0.7
        //    => ProficiencyFit = 0.7 (average of existing directions = 0.7 / 1 = 0.7)
        //
        // 3. LocationBonus: "Hà Nội" != "Đà Nẵng" -> 0.3
        //
        // 4. AvailabilityOverlap: Overlap MON 19:00-20:00 (60 mins) / min(120, 120) = 0.5
        //
        // Total = 0.40 * 0.5 + 0.25 * 0.7 + 0.15 * 0.3 + 0.20 * 0.5
        //       = 0.2000     + 0.1750     + 0.0450     + 0.1000
        //       = 0.5200
        // Percentage = 52%

        val result = MatchingCalculator.calculate(userA, userB)
        assertNotNull(result)
        assertEquals(0.52, result.totalScore)
        assertEquals(52, result.percentage)

        assertEquals(0.5, result.breakdown.skillMatch)
        assertEquals(0.7, result.breakdown.proficiencyFit)
        assertEquals(0.3, result.breakdown.locationBonus)
        assertEquals(0.5, result.breakdown.availabilityOverlap)

        assertEquals(0, result.matchedSkills.theyTeachYou.size)
        assertEquals(1, result.matchedSkills.youTeachThem.size)
    }

    @Test
    fun `calculate - Scenario 3 Best pair selection per direction, missing city and empty availability`() {
        // User A:
        //   HAVE: Design (id=10, level=5)
        //   WANT: French (id=20, level=1), Spanish (id=21, level=2)
        //   City: null (missing)
        //   Avail: empty
        val userA = UserMatchingProfile(
            userId = "user-a",
            fullName = "Hoàng E",
            city = null,
            availability = emptyList(),
            skills = listOf(
                UserSkillData(skillId = 10, skillName = "Design", type = "HAVE", proficiencyLevel = 5),
                UserSkillData(skillId = 20, skillName = "French", type = "WANT", proficiencyLevel = 1),
                UserSkillData(skillId = 21, skillName = "Spanish", type = "WANT", proficiencyLevel = 2)
            )
        )

        // User B:
        //   HAVE: French (id=20, level=5), Spanish (id=21, level=3)
        //   WANT: Design (id=10, level=1)
        //   City: "TP. Hồ Chí Minh"
        //   Avail: empty
        val userB = UserMatchingProfile(
            userId = "user-b",
            fullName = "Vũ F",
            city = "TP. Hồ Chí Minh",
            availability = emptyList(),
            skills = listOf(
                UserSkillData(skillId = 20, skillName = "French", type = "HAVE", proficiencyLevel = 5),
                UserSkillData(skillId = 21, skillName = "Spanish", type = "HAVE", proficiencyLevel = 3),
                UserSkillData(skillId = 10, skillName = "Design", type = "WANT", proficiencyLevel = 1)
            )
        )

        // Hand calculation:
        // 1. SkillMatch:
        //    - A->B has 2 matched skills: French (5 >= 1) and Spanish (3 >= 2) -> yes (+0.5)
        //    - B->A has 1 matched skill: Design (5 >= 1) -> yes (+0.5)
        //    => SkillMatch = 1.0
        //
        // 2. ProficiencyFit:
        //    - Direction A->B:
        //      * French: d = 5 - 1 = 4 (d >= 4) -> fit = 0.5
        //      * Spanish: d = 3 - 2 = 1 (d in 0..2) -> fit = 1.0
        //      * Best pair for A->B: max(0.5, 1.0) = 1.0
        //    - Direction B->A:
        //      * Design: d = 5 - 1 = 4 (d >= 4) -> fit = 0.5
        //      * Best pair for B->A: 0.5
        //    => ProficiencyFit = (1.0 + 0.5) / 2 = 0.75
        //
        // 3. LocationBonus: cityA is null -> 0.3
        //
        // 4. AvailabilityOverlap: availability empty -> 0.0
        //
        // Total = 0.40 * 1.0 + 0.25 * 0.75 + 0.15 * 0.3 + 0.20 * 0.0
        //       = 0.4000     + 0.1875      + 0.0450     + 0.0
        //       = 0.6325
        // Percentage = 63%

        val result = MatchingCalculator.calculate(userA, userB)
        assertNotNull(result)
        assertEquals(0.6325, result.totalScore)
        assertEquals(63, result.percentage)

        assertEquals(1.0, result.breakdown.skillMatch)
        assertEquals(0.75, result.breakdown.proficiencyFit)
        assertEquals(0.3, result.breakdown.locationBonus)
        assertEquals(0.0, result.breakdown.availabilityOverlap)

        assertEquals(2, result.matchedSkills.theyTeachYou.size)
        assertEquals(1, result.matchedSkills.youTeachThem.size)
    }

    @Test
    fun `calculate - Scenario 4 Exclude candidate when SkillMatch is 0`() {
        // Teacher levels strictly lower than Learner target levels -> no eligible pair
        val userA = UserMatchingProfile(
            userId = "user-a",
            fullName = "Ngô G",
            city = "Hà Nội",
            skills = listOf(
                UserSkillData(skillId = 1, skillName = "Kotlin", type = "HAVE", proficiencyLevel = 1),
                UserSkillData(skillId = 2, skillName = "English", type = "WANT", proficiencyLevel = 4)
            )
        )

        val userB = UserMatchingProfile(
            userId = "user-b",
            fullName = "Đặng H",
            city = "Hà Nội",
            skills = listOf(
                UserSkillData(skillId = 2, skillName = "English", type = "HAVE", proficiencyLevel = 2), // 2 < 4
                UserSkillData(skillId = 1, skillName = "Kotlin", type = "WANT", proficiencyLevel = 3)   // 1 < 3
            )
        )

        val result = MatchingCalculator.calculate(userA, userB)
        // Must be null (candidate excluded because SkillMatch = 0)
        assertNull(result)
    }
}
