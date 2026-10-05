package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.UserMatchingProfile
import com.skillexchange.api.models.matching.UserSkillData
import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test fixture tạo kịch bản 5 personas mẫu để đánh giá thuật toán tương thích (Matching Algorithm)
 * phục vụ cho việc ghi chép báo cáo kết quả đánh giá trọng số trong khóa luận tốt nghiệp.
 * Không thực hiện seed vào DB thật.
 */
class MatchingEvaluationFixtureTest {

    @Test
    fun `evaluate matching algorithm with 5 thesis personas and output markdown table`() {
        // ─────────────────────────────────────────────────────────────
        // 1. Khởi tạo Persona người gọi (Caller - Alice)
        // ─────────────────────────────────────────────────────────────
        val alice = UserMatchingProfile(
            userId = "caller-alice",
            fullName = "Nguyễn Thị Alice",
            city = "Hà Nội",
            availability = listOf(
                AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00"), // 2h
                AvailabilityWindowDto(day = "WED", from = "19:00", to = "21:00"), // 2h
                AvailabilityWindowDto(day = "SAT", from = "09:00", to = "11:00")  // 2h
            ), // Tổng: 6h
            skills = listOf(
                UserSkillData(skillId = 1, skillName = "Android (Kotlin)", type = "HAVE", proficiencyLevel = 4),
                UserSkillData(skillId = 2, skillName = "SQL", type = "HAVE", proficiencyLevel = 3),
                UserSkillData(skillId = 3, skillName = "Tiếng Anh", type = "WANT", proficiencyLevel = 2),
                UserSkillData(skillId = 4, skillName = "Guitar", type = "WANT", proficiencyLevel = 2)
            )
        )

        // ─────────────────────────────────────────────────────────────
        // 2. Khởi tạo 5 Candidates đại diện cho các trường hợp điển hình
        // ─────────────────────────────────────────────────────────────

        // Candidate 1 (Bob): Đối tác lý tưởng (Trao đổi 2 chiều hoàn hảo, cùng TP Hà Nội, lịch rảnh trùng khớp 100%)
        val bob = UserMatchingProfile(
            userId = "cand-bob",
            fullName = "Trần Văn Bob (Lý tưởng 2 chiều)",
            city = "Hà Nội",
            availability = listOf(
                AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00"), // 2h
                AvailabilityWindowDto(day = "WED", from = "19:00", to = "21:00")  // 2h
            ), // Tổng: 4h. min(6h, 4h) = 4h. Trùng MON 2h + WED 2h = 4h -> Overlap = 1.0
            skills = listOf(
                UserSkillData(skillId = 3, skillName = "Tiếng Anh", type = "HAVE", proficiencyLevel = 4), // d = 4 - 2 = 2 -> 1.0
                UserSkillData(skillId = 1, skillName = "Android (Kotlin)", type = "WANT", proficiencyLevel = 2) // d = 4 - 2 = 2 -> 1.0
            )
        )

        // Candidate 2 (Charlie): Khớp 2 chiều nhưng khác thành phố & trùng lịch một phần
        val charlie = UserMatchingProfile(
            userId = "cand-charlie",
            fullName = "Lê Charlie (2 chiều, khác TP)",
            city = "TP. Hồ Chí Minh",
            availability = listOf(
                AvailabilityWindowDto(day = "WED", from = "19:00", to = "21:00"), // 2h (trùng với Alice)
                AvailabilityWindowDto(day = "SUN", from = "10:00", to = "12:00")  // 2h
            ), // Tổng: 4h. min(6h, 4h) = 4h. Trùng WED 2h -> Overlap = 2/4 = 0.5
            skills = listOf(
                UserSkillData(skillId = 4, skillName = "Guitar", type = "HAVE", proficiencyLevel = 5), // d = 5 - 2 = 3 -> 0.7
                UserSkillData(skillId = 2, skillName = "SQL", type = "WANT", proficiencyLevel = 1)     // d = 3 - 1 = 2 -> 1.0
            )
        )

        // Candidate 3 (David): Khớp 1 chiều (chỉ Alice học David), cùng TP, trùng lịch một phần
        val david = UserMatchingProfile(
            userId = "cand-david",
            fullName = "Phạm David (1 chiều AB, lệch trình độ d=3)",
            city = "Hà Nội",
            availability = listOf(
                AvailabilityWindowDto(day = "SAT", from = "09:00", to = "11:00"), // 2h (trùng)
                AvailabilityWindowDto(day = "SUN", from = "09:00", to = "11:00")  // 2h
            ), // Tổng: 4h. Trùng SAT 2h -> Overlap = 2/4 = 0.5
            skills = listOf(
                UserSkillData(skillId = 3, skillName = "Tiếng Anh", type = "HAVE", proficiencyLevel = 5), // d = 5 - 2 = 3 -> 0.7
                UserSkillData(skillId = 10, skillName = "Hội họa", type = "WANT", proficiencyLevel = 1)   // Alice không có
            )
        )

        // Candidate 4 (Eva): Khớp 1 chiều (chỉ Eva học Alice), thiếu thành phố & không có lịch rảnh
        val eva = UserMatchingProfile(
            userId = "cand-eva",
            fullName = "Hoàng Eva (1 chiều BA, thiếu city/avail)",
            city = null,
            availability = emptyList(),
            skills = listOf(
                UserSkillData(skillId = 99, skillName = "Nấu ăn", type = "HAVE", proficiencyLevel = 4),   // Alice không muốn
                UserSkillData(skillId = 1, skillName = "Android (Kotlin)", type = "WANT", proficiencyLevel = 1) // d = 4 - 1 = 3 -> 0.7
            )
        )

        // Candidate 5 (Frank): Không có kỹ năng khớp (SkillMatch = 0 -> Loại bỏ)
        val frank = UserMatchingProfile(
            userId = "cand-frank",
            fullName = "Vũ Frank (Không trùng kỹ năng)",
            city = "Hà Nội",
            availability = listOf(
                AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00")
            ),
            skills = listOf(
                UserSkillData(skillId = 88, skillName = "Yoga", type = "HAVE", proficiencyLevel = 3),
                UserSkillData(skillId = 89, skillName = "Bơi lội", type = "WANT", proficiencyLevel = 2)
            )
        )

        val candidates = listOf(bob, charlie, david, eva, frank)

        // ─────────────────────────────────────────────────────────────
        // 3. Tính toán và xác minh kết quả từng Persona
        // ─────────────────────────────────────────────────────────────

        val scoreBob = MatchingCalculator.calculate(alice, bob)
        assertNotNull(scoreBob)
        assertEquals(1.0, scoreBob.totalScore)
        assertEquals(100, scoreBob.percentage)

        val scoreCharlie = MatchingCalculator.calculate(alice, charlie)
        assertNotNull(scoreCharlie)
        // 0.40*1.0 + 0.25*0.85 + 0.15*0.3 + 0.20*0.5 = 0.40 + 0.2125 + 0.045 + 0.10 = 0.7575
        assertEquals(0.7575, scoreCharlie.totalScore)
        assertEquals(76, scoreCharlie.percentage)

        val scoreDavid = MatchingCalculator.calculate(alice, david)
        assertNotNull(scoreDavid)
        // 0.40*0.5 + 0.25*0.7 + 0.15*1.0 + 0.20*0.5 = 0.20 + 0.175 + 0.15 + 0.10 = 0.625
        assertEquals(0.625, scoreDavid.totalScore)
        assertEquals(63, scoreDavid.percentage)

        val scoreEva = MatchingCalculator.calculate(alice, eva)
        assertNotNull(scoreEva)
        // 0.40*0.5 + 0.25*0.7 + 0.15*0.3 + 0.20*0.0 = 0.20 + 0.175 + 0.045 + 0.0 = 0.420
        assertEquals(0.42, scoreEva.totalScore)
        assertEquals(42, scoreEva.percentage)

        val scoreFrank = MatchingCalculator.calculate(alice, frank)
        assertNull(scoreFrank) // Đã loại bỏ do SkillMatch = 0

        // ─────────────────────────────────────────────────────────────
        // 4. Định dạng và in Markdown Table đánh giá khóa luận
        // ─────────────────────────────────────────────────────────────
        val report = buildString {
            appendLine("\n### BẢNG ĐÁNH GIÁ THỰC NGHIỆM THUẬT TOÁN MATCHING (DÙNG CHO KHÓA LUẬN)")
            appendLine("Công thức: `Score = 0.40*SkillMatch + 0.25*ProficiencyFit + 0.15*LocationBonus + 0.20*AvailabilityOverlap`")
            appendLine()
            appendLine("| Hạng | Ứng viên | SkillMatch (40%) | ProficiencyFit (25%) | Location (15%) | Availability (20%) | Điểm tổng | Tương thích (%) | Trạng thái |")
            appendLine("|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---|")

            val evaluatedList = candidates.map { cand ->
                val res = MatchingCalculator.calculate(alice, cand)
                cand to res
            }.sortedByDescending { it.second?.totalScore ?: -1.0 }

            var rank = 1
            for ((cand, score) in evaluatedList) {
                if (score != null) {
                    appendLine("| $rank | ${cand.fullName} | ${score.breakdown.skillMatch} | ${score.breakdown.proficiencyFit} | ${score.breakdown.locationBonus} | ${score.breakdown.availabilityOverlap} | **${score.totalScore}** | **${score.percentage}%** | Đề xuất |")
                    rank++
                } else {
                    appendLine("| - | ${cand.fullName} | 0.0 | 0.0 | - | - | **0.0000** | **0%** | *Loại (SkillMatch = 0)* |")
                }
            }
        }

        println(report)
    }
}
