package com.skillexchange.api.services.stats

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCalculatorTest {

    @Test
    fun `calculateMonthlySessions - returns 6 months with 0 count when timestamps list is empty`() {
        val now = LocalDateTime.of(2026, 5, 15, 10, 0, 0)
        val result = StatsCalculator.calculateMonthlySessions(emptyList(), now)

        assertEquals(6, result.size)
        assertEquals("2025-12", result[0].month)
        assertEquals("12/2025", result[0].label)
        assertEquals(0, result[0].count)

        assertEquals("2026-01", result[1].month)
        assertEquals("01/2026", result[1].label)
        assertEquals(0, result[1].count)

        assertEquals("2026-02", result[2].month)
        assertEquals("2026-03", result[3].month)
        assertEquals("2026-04", result[4].month)

        assertEquals("2026-05", result[5].month)
        assertEquals("05/2026", result[5].label)
        assertEquals(0, result[5].count)
    }

    @Test
    fun `calculateMonthlySessions - verifies exact month boundaries, start and end of month`() {
        val now = LocalDateTime.of(2026, 5, 20, 14, 0, 0)

        val timestamps = listOf(
            // Điểm mốc đầu tháng 5 (01/05 00:00:00)
            LocalDateTime.of(2026, 5, 1, 0, 0, 0),
            // Điểm mốc giữa tháng 5
            LocalDateTime.of(2026, 5, 15, 12, 30, 0),
            // Điểm mốc cuối tháng 4 (30/04 23:59:59)
            LocalDateTime.of(2026, 4, 30, 23, 59, 59),
            // Điểm mốc đầu tháng 4 (01/04 00:00:00)
            LocalDateTime.of(2026, 4, 1, 0, 0, 0),
            // Quá cũ (> 6 tháng trước: 2025-10-31)
            LocalDateTime.of(2025, 10, 31, 23, 59, 59)
        )

        val result = StatsCalculator.calculateMonthlySessions(timestamps, now)

        assertEquals(6, result.size)
        // Tháng 5 (chỉ số 5) có 2 phiên
        assertEquals("2026-05", result[5].month)
        assertEquals(2, result[5].count)

        // Tháng 4 (chỉ số 4) có 2 phiên
        assertEquals("2026-04", result[4].month)
        assertEquals(2, result[4].count)

        // Tháng 3, 2, 1, 12 có 0 phiên
        assertEquals(0, result[3].count)
        assertEquals(0, result[2].count)
        assertEquals(0, result[1].count)
        assertEquals(0, result[0].count)
    }

    @Test
    fun `calculateMonthlySessions - handles leap year February 29 and year rollover correctly`() {
        // Giả sử mốc thời gian là tháng 4/2024 (năm nhuận)
        val now = LocalDateTime.of(2024, 4, 10, 12, 0, 0)

        val timestamps = listOf(
            // Năm trước: 31/12/2023 lúc 23:59:59 (chuyển năm)
            LocalDateTime.of(2023, 12, 31, 23, 59, 59),
            // 01/01/2024 lúc 00:00:00
            LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            // Ngày 29/02/2024 (ngày nhuận 29 tháng 2)
            LocalDateTime.of(2024, 2, 29, 23, 59, 59)
        )

        val result = StatsCalculator.calculateMonthlySessions(timestamps, now)

        // 6 tháng: 2023-11 (idx 0), 2023-12 (idx 1), 2024-01 (idx 2), 2024-02 (idx 3), 2024-03 (idx 4), 2024-04 (idx 5)
        assertEquals(6, result.size)
        assertEquals("2023-12", result[1].month)
        assertEquals(1, result[1].count)

        assertEquals("2024-01", result[2].month)
        assertEquals(1, result[2].count)

        assertEquals("2024-02", result[3].month)
        assertEquals(1, result[3].count)
    }

    @Test
    fun `calculateRadarScores - empty skills returns 6 axes with 0 score`() {
        val result = StatsCalculator.calculateRadarScores(emptyList())

        assertEquals(6, result.size)
        assertTrue(result.all { it.score == 0.0 && it.skillCount == 0 })
        assertEquals("Lập trình", result[0].categoryName)
        assertEquals("Ngoại ngữ", result[1].categoryName)
        assertEquals("Âm nhạc", result[2].categoryName)
        assertEquals("Thiết kế", result[3].categoryName)
        assertEquals("Nấu ăn", result[4].categoryName)
        assertEquals("Thể thao", result[5].categoryName)
    }

    @Test
    fun `calculateRadarScores - calculates maximum proficiency level per category`() {
        val skills = listOf(
            Triple(1, 101, 3), // Lập trình Lv 3
            Triple(1, 102, 5), // Lập trình Lv 5
            Triple(2, 201, 4), // Ngoại ngữ Lv 4
            Triple(4, 401, 2)  // Thiết kế Lv 2
        )

        val result = StatsCalculator.calculateRadarScores(skills)

        val programming = result.first { it.axisId == 1 }
        assertEquals(5.0, programming.score)
        assertEquals(2, programming.skillCount)

        val language = result.first { it.axisId == 2 }
        assertEquals(4.0, language.score)
        assertEquals(1, language.skillCount)

        val music = result.first { it.axisId == 3 }
        assertEquals(0.0, music.score)
        assertEquals(0, music.skillCount)

        val design = result.first { it.axisId == 4 }
        assertEquals(2.0, design.score)
        assertEquals(1, design.skillCount)
    }

    @Test
    fun `calculateSkillGaps - suggests skills using authentic matching data and ranks by unlocked matches`() {
        val callerId = "user-caller"
        val callerHaveSkillIds = setOf(1) // Caller dạy Kotlin (skill 1)
        val callerAllSkillIds = setOf(1, 2) // Caller đã có Kotlin (1) và muốn Tiếng Anh (2)

        val allSkills = listOf(
            // User B: Muốn học Kotlin (1), Có dạy Piano (skill 3, category Âm nhạc)
            UserSkillRecord("user-b", 1, "Kotlin", 1, "Lập trình", "WANT"),
            UserSkillRecord("user-b", 3, "Piano", 3, "Âm nhạc", "HAVE"),

            // User C: Muốn học Kotlin (1), Có dạy Piano (skill 3, category Âm nhạc) và Guitar (skill 4, category Âm nhạc)
            UserSkillRecord("user-c", 1, "Kotlin", 1, "Lập trình", "WANT"),
            UserSkillRecord("user-c", 3, "Piano", 3, "Âm nhạc", "HAVE"),
            UserSkillRecord("user-c", 4, "Guitar", 3, "Âm nhạc", "HAVE"),

            // User D: Muốn học Kotlin (1), Có dạy Tiếng Anh (skill 2 - caller đã có nên bỏ qua)
            UserSkillRecord("user-d", 1, "Kotlin", 1, "Lập trình", "WANT"),
            UserSkillRecord("user-d", 2, "Tiếng Anh", 2, "Ngoại ngữ", "HAVE")
        )

        val suggestions = StatsCalculator.calculateSkillGaps(
            callerId = callerId,
            callerHaveSkillIds = callerHaveSkillIds,
            callerAllSkillIds = callerAllSkillIds,
            allUserSkills = allSkills
        )

        assertEquals(2, suggestions.size)
        // Kỹ năng 1: Piano (skill 3) mở thêm 2 matches (user-b và user-c)
        val first = suggestions[0]
        assertEquals(3, first.skillId)
        assertEquals("Piano", first.skillName)
        assertEquals(2, first.unlockedMatchesCount)

        // Kỹ năng 2: Guitar (skill 4) mở thêm 1 match (user-c)
        val second = suggestions[1]
        assertEquals(4, second.skillId)
        assertEquals("Guitar", second.skillName)
        assertEquals(1, second.unlockedMatchesCount)
    }

    @Test
    fun `calculateSkillGaps - returns empty when caller has no HAVE skills`() {
        val suggestions = StatsCalculator.calculateSkillGaps(
            callerId = "user-caller",
            callerHaveSkillIds = emptySet(),
            callerAllSkillIds = emptySet(),
            allUserSkills = emptyList()
        )
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `rankLeaderboard - sorts by score DESC, then totalExchanges DESC, and caps at top 10`() {
        val candidates = (1..15).map { i ->
            LeaderboardCandidate(
                userId = "u$i",
                fullName = "User $i",
                score = (i % 5).toDouble() + 1.0, // 1.0 .. 5.0
                totalExchanges = i * 2,
                ratingCount = i
            )
        }

        val ranked = StatsCalculator.rankLeaderboard(candidates)

        assertEquals(10, ranked.size)
        // Kiểm tra thứ hạng 1..10 liên tục
        assertEquals((1..10).toList(), ranked.map { it.rank })

        // Phần tử đầu tiên có điểm cao nhất
        assertTrue(ranked[0].score >= ranked[1].score)
        for (i in 0 until ranked.size - 1) {
            assertTrue(
                ranked[i].score > ranked[i + 1].score ||
                        (ranked[i].score == ranked[i + 1].score && ranked[i].totalExchanges >= ranked[i + 1].totalExchanges)
            )
        }
    }
}
