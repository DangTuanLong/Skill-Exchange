package com.skillexchange.api.services.stats

import com.skillexchange.api.models.stats.LeaderboardItemDto
import com.skillexchange.api.models.stats.MonthlySessionDto
import com.skillexchange.api.models.stats.RadarAxisDto
import com.skillexchange.api.models.stats.SkillGapSuggestionDto
import java.time.LocalDateTime
import java.time.YearMonth

data class UserSkillRecord(
    val userId: String,
    val skillId: Int,
    val skillName: String,
    val categoryId: Int,
    val categoryName: String,
    val type: String // "HAVE" hoặc "WANT"
)

data class LeaderboardCandidate(
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null,
    val score: Double,
    val badge: String? = null,
    val totalExchanges: Int,
    val ratingCount: Int
)

object StatsCalculator {

    val DEFAULT_RADAR_AXES = listOf(
        1 to "Lập trình",
        2 to "Ngoại ngữ",
        3 to "Âm nhạc",
        4 to "Thiết kế",
        5 to "Nấu ăn",
        6 to "Thể thao"
    )

    /**
     * Tính toán số phiên theo 6 tháng gần nhất (bao gồm cả tháng hiện tại).
     * Xử lý chính xác các mốc biên: đầu tháng (00:00:00), cuối tháng (23:59:59.999), năm nhuận và chuyển năm.
     */
    fun calculateMonthlySessions(
        completedTimestamps: List<LocalDateTime>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<MonthlySessionDto> {
        val currentYm = YearMonth.from(now)
        return (5 downTo 0).map { offset ->
            val ym = currentYm.minusMonths(offset.toLong())
            val startOfMonth = ym.atDay(1).atStartOfDay()
            val startOfNextMonth = ym.plusMonths(1).atDay(1).atStartOfDay()

            val countInMonth = completedTimestamps.count { time ->
                !time.isBefore(startOfMonth) && time.isBefore(startOfNextMonth)
            }

            val monthStr = ym.toString() // "YYYY-MM"
            val labelStr = "${ym.monthValue.toString().padStart(2, '0')}/${ym.year}"
            MonthlySessionDto(
                month = monthStr,
                label = labelStr,
                count = countInMonth
            )
        }
    }

    /**
     * Tính toán radar 6 trục kỹ năng dựa trên danh mục chuẩn và cấp độ thành thạo cao nhất.
     */
    fun calculateRadarScores(
        userHaveSkills: List<Triple<Int, Int, Int>> // (categoryId, skillId, proficiencyLevel)
    ): List<RadarAxisDto> {
        return DEFAULT_RADAR_AXES.map { (catId, catName) ->
            val skillsInCat = userHaveSkills.filter { it.first == catId }
            val maxProficiency = skillsInCat.maxOfOrNull { it.third } ?: 0
            RadarAxisDto(
                axisId = catId,
                categoryName = catName,
                score = maxProficiency.toDouble(),
                skillCount = skillsInCat.size
            )
        }
    }

    /**
     * Gợi ý khoảng trống kỹ năng (Skill Gap) dựa trên dữ liệu matching thực tế (không bịa số).
     */
    fun calculateSkillGaps(
        callerId: String,
        callerHaveSkillIds: Set<Int>,
        callerAllSkillIds: Set<Int>,
        allUserSkills: List<UserSkillRecord>
    ): List<SkillGapSuggestionDto> {
        if (callerHaveSkillIds.isEmpty()) return emptyList()

        // 1. Tìm các người dùng B khác đang có nhu cầu học (WANT) ít nhất 1 kỹ năng trong callerHaveSkillIds
        val candidatePartners = allUserSkills.filter {
            it.userId != callerId && it.type == "WANT" && it.skillId in callerHaveSkillIds
        }.map { it.userId }.toSet()

        if (candidatePartners.isEmpty()) return emptyList()

        // 2. Tìm tất cả các kỹ năng mà các candidatePartners này có thể dạy (HAVE) nhưng caller chưa có (không có trong HAVE lẫn WANT)
        val partnerOffers = allUserSkills.filter {
            it.userId in candidatePartners && it.type == "HAVE" && it.skillId !in callerAllSkillIds
        }

        if (partnerOffers.isEmpty()) return emptyList()

        // 3. Gom nhóm theo skillId và đếm số lượng người dạy riêng biệt (distinct users)
        return partnerOffers.groupBy { it.skillId }
            .map { (skillId, records) ->
                val distinctUsersCount = records.map { it.userId }.distinct().size
                val first = records.first()
                SkillGapSuggestionDto(
                    skillId = skillId,
                    skillName = first.skillName,
                    categoryName = first.categoryName,
                    unlockedMatchesCount = distinctUsersCount,
                    reason = "Có $distinctUsersCount thành viên đang cần học kỹ năng của bạn và có thể dạy kỹ năng này."
                )
            }
            .sortedByDescending { it.unlockedMatchesCount }
            .take(3)
    }

    /**
     * Sắp xếp và xếp hạng Top 10 Bảng xếp hạng.
     */
    fun rankLeaderboard(candidates: List<LeaderboardCandidate>): List<LeaderboardItemDto> {
        return candidates
            .sortedWith(
                compareByDescending<LeaderboardCandidate> { it.score }
                    .thenByDescending { it.totalExchanges }
                    .thenByDescending { it.ratingCount }
                    .thenBy { it.fullName }
            )
            .take(10)
            .mapIndexed { index, c ->
                LeaderboardItemDto(
                    rank = index + 1,
                    userId = c.userId,
                    fullName = c.fullName,
                    avatarUrl = c.avatarUrl,
                    city = c.city,
                    score = c.score,
                    badge = c.badge,
                    totalExchanges = c.totalExchanges,
                    ratingCount = c.ratingCount
                )
            }
    }
}
