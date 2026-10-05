package com.skillexchange.api.services.matching

import com.skillexchange.api.models.matching.*
import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlin.math.roundToInt

object MatchingCalculator {

    const val WEIGHT_SKILL_MATCH = 0.40
    const val WEIGHT_PROFICIENCY_FIT = 0.25
    const val WEIGHT_LOCATION_BONUS = 0.15
    const val WEIGHT_AVAILABILITY_OVERLAP = 0.20

    /**
     * Tính toán điểm tương thích giữa user A (người gọi) và user B (ứng viên).
     * Trả về null nếu SkillMatch = 0.0 (không có kỹ năng phù hợp ở bất kỳ chiều nào).
     */
    fun calculate(userA: UserMatchingProfile, userB: UserMatchingProfile): MatchingScoreResponse? {
        val aHave = userA.skills.filter { it.type.equals("HAVE", ignoreCase = true) }
        val aWant = userA.skills.filter { it.type.equals("WANT", ignoreCase = true) }
        val bHave = userB.skills.filter { it.type.equals("HAVE", ignoreCase = true) }
        val bWant = userB.skills.filter { it.type.equals("WANT", ignoreCase = true) }

        // Chiều A -> B: B dạy, A học (B có HAVE, A có WANT cùng skillId và bHave.level >= aWant.level)
        val eligibleAB = mutableListOf<MatchedSkillDto>()
        for (want in aWant) {
            val matchingHave = bHave.filter { it.skillId == want.skillId && it.proficiencyLevel >= want.proficiencyLevel }
            for (have in matchingHave) {
                eligibleAB.add(
                    MatchedSkillDto(
                        skillId = want.skillId,
                        skillName = have.skillName.ifBlank { want.skillName },
                        teacherLevel = have.proficiencyLevel,
                        learnerLevel = want.proficiencyLevel
                    )
                )
            }
        }

        // Chiều B -> A: A dạy, B học (A có HAVE, B có WANT cùng skillId và aHave.level >= bWant.level)
        val eligibleBA = mutableListOf<MatchedSkillDto>()
        for (want in bWant) {
            val matchingHave = aHave.filter { it.skillId == want.skillId && it.proficiencyLevel >= want.proficiencyLevel }
            for (have in matchingHave) {
                eligibleBA.add(
                    MatchedSkillDto(
                        skillId = want.skillId,
                        skillName = have.skillName.ifBlank { want.skillName },
                        teacherLevel = have.proficiencyLevel,
                        learnerLevel = want.proficiencyLevel
                    )
                )
            }
        }

        val hasAB = eligibleAB.isNotEmpty()
        val hasBA = eligibleBA.isNotEmpty()

        // Loại ứng viên nếu SkillMatch = 0
        if (!hasAB && !hasBA) {
            return null
        }

        // 1. SkillMatch: +0.5 cho mỗi chiều có ít nhất 1 cặp hợp lệ
        val skillMatch = (if (hasAB) 0.5 else 0.0) + (if (hasBA) 0.5 else 0.0)

        // 2. ProficiencyFit: Lấy cặp tốt nhất mỗi chiều rồi trung bình các chiều có tồn tại
        val bestAB = if (hasAB) eligibleAB.maxOf { calculateProficiencyFit(it.teacherLevel, it.learnerLevel) } else null
        val bestBA = if (hasBA) eligibleBA.maxOf { calculateProficiencyFit(it.teacherLevel, it.learnerLevel) } else null

        val proficiencyFit = when {
            bestAB != null && bestBA != null -> (bestAB + bestBA) / 2.0
            bestAB != null -> bestAB
            bestBA != null -> bestBA
            else -> 0.0
        }

        // 3. LocationBonus: Cùng city = 1.0; khác city hoặc thiếu city = 0.3
        val locationBonus = calculateLocationBonus(userA.city, userB.city)

        // 4. AvailabilityOverlap: Giờ chồng / min(tổng giờ rảnh hai bên), 0 nếu thiếu
        val availabilityOverlap = calculateAvailabilityOverlap(userA.availability, userB.availability)

        // Tổng điểm
        val rawTotalScore = (WEIGHT_SKILL_MATCH * skillMatch) +
                (WEIGHT_PROFICIENCY_FIT * proficiencyFit) +
                (WEIGHT_LOCATION_BONUS * locationBonus) +
                (WEIGHT_AVAILABILITY_OVERLAP * availabilityOverlap)

        val totalScore = (Math.round(rawTotalScore * 10000.0) / 10000.0).coerceIn(0.0, 1.0)
        val percentage = (totalScore * 100).roundToInt().coerceIn(0, 100)

        return MatchingScoreResponse(
            targetUserId = userB.userId,
            totalScore = totalScore,
            percentage = percentage,
            breakdown = MatchingScoreBreakdown(
                skillMatch = Math.round(skillMatch * 10000.0) / 10000.0,
                proficiencyFit = Math.round(proficiencyFit * 10000.0) / 10000.0,
                locationBonus = Math.round(locationBonus * 10000.0) / 10000.0,
                availabilityOverlap = Math.round(availabilityOverlap * 10000.0) / 10000.0
            ),
            matchedSkills = MatchedSkillsBreakdown(
                theyTeachYou = eligibleAB,
                youTeachThem = eligibleBA
            )
        )
    }

    /**
     * Tính ProficiencyFit cho 1 cặp kỹ năng hợp lệ: d = teacherHave - learnerTargetWant
     * d in 0..2 -> 1.0
     * d == 3    -> 0.7
     * d >= 4    -> 0.5
     * d < 0     -> 0.0 (không hợp lệ)
     */
    fun calculateProficiencyFit(teacherLevel: Int, learnerLevel: Int): Double {
        val d = teacherLevel - learnerLevel
        return when {
            d < 0 -> 0.0
            d in 0..2 -> 1.0
            d == 3 -> 0.7
            else -> 0.5 // d >= 4
        }
    }

    /**
     * LocationBonus: Cùng city = 1.0; khác city hoặc thiếu city = 0.3
     */
    fun calculateLocationBonus(cityA: String?, cityB: String?): Double {
        if (cityA.isNullOrBlank() || cityB.isNullOrBlank()) {
            return 0.3
        }
        return if (cityA.trim().equals(cityB.trim(), ignoreCase = true)) 1.0 else 0.3
    }

    /**
     * AvailabilityOverlap = overlap_hours / min(tổng giờ rảnh hai bên).
     * Bằng 0 nếu một trong hai bên không có lịch rảnh.
     */
    fun calculateAvailabilityOverlap(
        availA: List<AvailabilityWindowDto>,
        availB: List<AvailabilityWindowDto>
    ): Double {
        if (availA.isEmpty() || availB.isEmpty()) {
            return 0.0
        }

        val totalMinutesA = availA.sumOf { windowDurationMinutes(it) }
        val totalMinutesB = availB.sumOf { windowDurationMinutes(it) }
        val minTotalMinutes = minOf(totalMinutesA, totalMinutesB)

        if (minTotalMinutes <= 0) {
            return 0.0
        }

        val groupedA = availA.groupBy { it.day.uppercase() }
        val groupedB = availB.groupBy { it.day.uppercase() }

        var overlapMinutes = 0
        for ((day, windowsA) in groupedA) {
            val windowsB = groupedB[day] ?: continue
            for (wA in windowsA) {
                val startA = timeToMinutes(wA.from)
                val endA = timeToMinutes(wA.to)
                if (startA >= endA) continue

                for (wB in windowsB) {
                    val startB = timeToMinutes(wB.from)
                    val endB = timeToMinutes(wB.to)
                    if (startB >= endB) continue

                    val overlapStart = maxOf(startA, startB)
                    val overlapEnd = minOf(endA, endB)
                    if (overlapEnd > overlapStart) {
                        overlapMinutes += (overlapEnd - overlapStart)
                    }
                }
            }
        }

        val ratio = overlapMinutes.toDouble() / minTotalMinutes.toDouble()
        return (Math.round(ratio * 10000.0) / 10000.0).coerceIn(0.0, 1.0)
    }

    private fun windowDurationMinutes(w: AvailabilityWindowDto): Int {
        val from = timeToMinutes(w.from)
        val to = timeToMinutes(w.to)
        return maxOf(0, to - from)
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.trim().split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }
}
