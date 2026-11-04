package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.MatchingSuggestionDto
import com.skillexchange.api.models.matching.PotentialMatchDto
import com.skillexchange.api.models.matching.PotentialMatchSkillDto
import com.skillexchange.api.models.matching.UserMatchingProfile
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Pure logic helper for matching calculations, sorting, filtering, and cursor pagination.
 */
object MatchingLogicHelper {

    fun encodeCursor(score: Int, userId: String): String {
        val raw = "${score}_$userId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    fun decodeCursor(cursor: String): Pair<Int, String>? {
        if (cursor.isBlank()) return null
        val candidates = mutableListOf<String>()
        try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
            candidates.add(decoded)
        } catch (_: Exception) {}
        candidates.add(cursor)

        for (raw in candidates) {
            val parts = raw.split("_", limit = 2)
            if (parts.size == 2) {
                val score = parts[0].toIntOrNull()
                if (score != null && parts[1].isNotBlank()) {
                    return score to parts[1]
                }
            }
        }
        return null
    }

    fun calculatePotentialScore(
        bestTeachLevel: Int,
        sSched: Double,
        sCity: Double,
        sRep: Double = 0.6
    ): Int {
        val sHaveLevel = bestTeachLevel / 5.0
        val rawScore = (0.35 * sHaveLevel) + (0.30 * sSched) + (0.20 * sCity) + (0.15 * sRep)
        return (rawScore * 100).roundToInt().coerceIn(0, 100)
    }

    fun rankSuggestions(
        callerProfile: UserMatchingProfile,
        candidates: List<UserMatchingProfile>,
        cursor: String? = null,
        limit: Int = 20
    ): List<MatchingSuggestionDto> {
        val clampedLimit = limit.coerceIn(1, 50)

        val suggestions = candidates.mapNotNull { candProfile ->
            val score = MatchingCalculator.calculate(callerProfile, candProfile) ?: return@mapNotNull null
            MatchingSuggestionDto(
                userId = candProfile.userId,
                fullName = candProfile.fullName,
                avatarUrl = candProfile.avatarUrl,
                city = candProfile.city,
                totalScore = score.totalScore,
                percentage = score.percentage,
                breakdown = score.breakdown,
                matchedSkills = score.matchedSkills,
                availablePairsCount = score.breakdown.availablePairsCount
            )
        }

        // Sắp xếp tất định theo (điểm làm tròn cố định dạng số nguyên DESC, candidateId ASC)
        val sorted = suggestions.sortedWith(
            compareByDescending<MatchingSuggestionDto> { it.percentage }
                .thenBy { it.userId }
        )

        // Phân trang cursor số nguyên
        val cursorParsed = cursor?.let { decodeCursor(it) }
        val filtered = if (cursorParsed != null) {
            val (cScore, cId) = cursorParsed
            sorted.filter { it.percentage < cScore || (it.percentage == cScore && it.userId > cId) }
        } else {
            sorted
        }

        return filtered.take(clampedLimit)
    }

    fun rankPotentialMatches(
        callerProfile: UserMatchingProfile,
        candidates: List<UserMatchingProfile>,
        cursor: String? = null,
        limit: Int = 20
    ): List<PotentialMatchDto> {
        val clampedLimit = limit.coerceIn(1, 50)

        val callerHaveSkills = callerProfile.skills.filter { it.type.equals("HAVE", ignoreCase = true) }
        val callerWantSkills = callerProfile.skills.filter { it.type.equals("WANT", ignoreCase = true) }
        val callerWantIds = callerWantSkills.map { it.skillId }.toSet()
        val callerHaveIds = callerHaveSkills.map { it.skillId }.toSet()

        if (callerHaveSkills.isEmpty()) {
            return emptyList()
        }

        val potentials = candidates.mapNotNull { candProfile ->
            val candSkills = candProfile.skills
            val candHave = candSkills.filter { it.type.equals("HAVE", ignoreCase = true) }
            val candWant = candSkills.filter { it.type.equals("WANT", ignoreCase = true) }

            // Caller dạy được kỹ năng mà candidate cần (callerHave.level >= candWant.level)
            val eligibleTeachSkills = callerHaveSkills.filter { callerH ->
                val want = candWant.firstOrNull { it.skillId == callerH.skillId }
                want != null && callerH.proficiencyLevel >= want.proficiencyLevel
            }
            if (eligibleTeachSkills.isEmpty()) return@mapNotNull null

            // Kỹ năng candidate dạy mà caller CHƯA có trong WANT và HAVE
            val eligibleSuggestedWantSkills = candHave.filter { candH ->
                candH.skillId !in callerWantIds && candH.skillId !in callerHaveIds
            }
            if (eligibleSuggestedWantSkills.isEmpty()) return@mapNotNull null

            // Kiểm tra loại trừ nếu đã khớp 2 chiều (nếu khớp 2 chiều thì thuộc về suggestions chính, không phải potential)
            val isTwoWay = candHave.any { candH ->
                val want = callerWantSkills.firstOrNull { it.skillId == candH.skillId }
                want != null && candH.proficiencyLevel >= want.proficiencyLevel
            }
            if (isTwoWay) return@mapNotNull null

            // Chọn kỹ năng dạy tốt nhất và kỹ năng gợi ý tốt nhất (trình độ cao nhất)
            val bestTeach = eligibleTeachSkills.maxByOrNull { it.proficiencyLevel }!!
            val bestSuggestedWant = eligibleSuggestedWantSkills.maxByOrNull { it.proficiencyLevel }!!

            // Công thức xếp hạng riêng PotentialScore:
            // PotentialScore = round(100 * (0.35 * have_level + 0.30 * sched + 0.20 * city + 0.15 * rep))
            val sSched = MatchingCalculator.calculateAvailabilityOverlap(callerProfile.availability, candProfile.availability)
            val sCity = MatchingCalculator.calculateLocationBonus(callerProfile.city, candProfile.city)
            val sRep = 0.6 // Điểm uy tín chuẩn hóa mặc định cho đối tác mới

            val potentialScore = calculatePotentialScore(bestTeach.proficiencyLevel, sSched, sCity, sRep)

            PotentialMatchDto(
                userId = candProfile.userId,
                fullName = candProfile.fullName,
                avatarUrl = candProfile.avatarUrl,
                city = candProfile.city,
                canTeachSkill = PotentialMatchSkillDto(
                    skillId = bestTeach.skillId,
                    skillName = bestTeach.skillName,
                    proficiencyLevel = bestTeach.proficiencyLevel
                ),
                suggestedWantSkill = PotentialMatchSkillDto(
                    skillId = bestSuggestedWant.skillId,
                    skillName = bestSuggestedWant.skillName,
                    proficiencyLevel = bestSuggestedWant.proficiencyLevel
                ),
                potentialScore = potentialScore
            )
        }

        // Sắp xếp tất định theo (potentialScore DESC, userId ASC)
        val sorted = potentials.sortedWith(
            compareByDescending<PotentialMatchDto> { it.potentialScore }
                .thenBy { it.userId }
        )

        // Phân trang cursor
        val cursorParsed = cursor?.let { decodeCursor(it) }
        val filtered = if (cursorParsed != null) {
            val (cScore, cId) = cursorParsed
            sorted.filter { it.potentialScore < cScore || (it.potentialScore == cScore && it.userId > cId) }
        } else {
            sorted
        }

        return filtered.take(clampedLimit)
    }
}

