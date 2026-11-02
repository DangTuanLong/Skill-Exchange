package com.skillexchange.api.services.matching

import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.matching.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.Base64
import kotlin.math.roundToInt

open class MatchingService {

    companion object {
        const val SUGGESTION_COOLDOWN_DAYS = 14L
    }

    /**
     * Gợi ý đối tác trao đổi kỹ năng cho callerUserId (DEC-028).
     * Loại bỏ ở cấp CSDL bằng NOT EXISTS/anti-join:
     * - Chính người gọi
     * - Đối tác đang active (PENDING, ACCEPTED)
     * - Đối tác đã hoàn thành (COMPLETED)
     * - Đối tác bị REJECTED hoặc CANCELLED trong vòng 14 ngày (SUGGESTION_COOLDOWN_DAYS)
     */
    open fun getSuggestions(
        callerUserId: String,
        limit: Int = 20,
        cursor: String? = null
    ): List<MatchingSuggestionDto> = transaction {
        val clampedLimit = limit.coerceIn(1, 50)

        // 1. Lấy profile và skills của caller
        val callerProfileRow = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq callerUserId }
            .firstOrNull() ?: return@transaction emptyList()

        val callerSkills = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { UserSkillsTable.userId eq callerUserId }
            .map {
                UserSkillData(
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel]
                )
            }

        if (callerSkills.isEmpty()) {
            return@transaction emptyList()
        }

        val callerProfile = UserMatchingProfile(
            userId = callerUserId,
            fullName = callerProfileRow[ProfilesTable.fullName],
            avatarUrl = callerProfileRow[ProfilesTable.avatarUrl],
            city = callerProfileRow[ProfilesTable.city],
            availability = callerProfileRow[ProfilesTable.availability] ?: emptyList(),
            skills = callerSkills
        )

        // 2. Tìm các ứng viên có tiềm năng khớp (loại bỏ chính caller)
        val wantSkillIds = callerSkills.filter { it.type.equals("WANT", ignoreCase = true) }.map { it.skillId }
        val haveSkillIds = callerSkills.filter { it.type.equals("HAVE", ignoreCase = true) }.map { it.skillId }

        val candidateOp: Op<Boolean> = when {
            wantSkillIds.isNotEmpty() && haveSkillIds.isNotEmpty() -> {
                ((UserSkillsTable.type eq "HAVE") and (UserSkillsTable.skillId inList wantSkillIds)) or
                ((UserSkillsTable.type eq "WANT") and (UserSkillsTable.skillId inList haveSkillIds))
            }
            wantSkillIds.isNotEmpty() -> {
                (UserSkillsTable.type eq "HAVE") and (UserSkillsTable.skillId inList wantSkillIds)
            }
            haveSkillIds.isNotEmpty() -> {
                (UserSkillsTable.type eq "WANT") and (UserSkillsTable.skillId inList haveSkillIds)
            }
            else -> return@transaction emptyList()
        }

        // Lọc loại trừ phía CSDL bằng NOT EXISTS / anti-join (DEC-028)
        val cooldownCutoff = LocalDateTime.now().minusDays(SUGGESTION_COOLDOWN_DAYS)
        val exclusionCondition = notExists(
            ExchangeRequestsTable.select(intLiteral(1)).where {
                (
                    ((ExchangeRequestsTable.senderId eq callerUserId) and (ExchangeRequestsTable.receiverId eq UserSkillsTable.userId)) or
                    ((ExchangeRequestsTable.receiverId eq callerUserId) and (ExchangeRequestsTable.senderId eq UserSkillsTable.userId))
                ) and (
                    (ExchangeRequestsTable.status inList listOf(
                        ExchangeStatus.PENDING.name,
                        ExchangeStatus.ACCEPTED.name,
                        ExchangeStatus.COMPLETED.name
                    )) or
                    (
                        (ExchangeRequestsTable.status inList listOf(
                            ExchangeStatus.REJECTED.name,
                            ExchangeStatus.CANCELLED.name
                        )) and (
                            (ExchangeRequestsTable.resolvedAt.isNotNull() and (ExchangeRequestsTable.resolvedAt greater cooldownCutoff)) or
                            (ExchangeRequestsTable.resolvedAt.isNull() and (ExchangeRequestsTable.updatedAt greater cooldownCutoff))
                        )
                    )
                )
            }
        )

        val candidateUserIds = UserSkillsTable
            .select(UserSkillsTable.userId)
            .where { (UserSkillsTable.userId neq callerUserId) and candidateOp and exclusionCondition }
            .withDistinct()
            .map { it[UserSkillsTable.userId] }

        if (candidateUserIds.isEmpty()) {
            return@transaction emptyList()
        }

        // 3. Lấy profiles và toàn bộ skills của các candidateUserIds
        val candidateProfileRows = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList candidateUserIds }
            .associateBy { it[ProfilesTable.userId] }

        val candidateSkillsMap = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { UserSkillsTable.userId inList candidateUserIds }
            .map {
                it[UserSkillsTable.userId] to UserSkillData(
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel]
                )
            }
            .groupBy({ it.first }, { it.second })

        // 4. Tính toán điểm cho từng ứng viên (công thức và trọng số MatchingCalculator KHÔNG đổi)
        val suggestions = candidateUserIds.mapNotNull { candidateId ->
            val pRow = candidateProfileRows[candidateId] ?: return@mapNotNull null
            val candProfile = UserMatchingProfile(
                userId = candidateId,
                fullName = pRow[ProfilesTable.fullName],
                avatarUrl = pRow[ProfilesTable.avatarUrl],
                city = pRow[ProfilesTable.city],
                availability = pRow[ProfilesTable.availability] ?: emptyList(),
                skills = candidateSkillsMap[candidateId] ?: emptyList()
            )

            val score = MatchingCalculator.calculate(callerProfile, candProfile) ?: return@mapNotNull null
            MatchingSuggestionDto(
                userId = candidateId,
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

        // 5. Sắp xếp tất định theo (điểm làm tròn cố định dạng số nguyên DESC, candidateId ASC)
        val sorted = suggestions.sortedWith(
            compareByDescending<MatchingSuggestionDto> { it.percentage }
                .thenBy { it.userId }
        )

        // 6. Phân trang cursor số nguyên
        val cursorParsed = cursor?.let { decodeCursor(it) }
        val filtered = if (cursorParsed != null) {
            val (cScore, cId) = cursorParsed
            sorted.filter { it.percentage < cScore || (it.percentage == cScore && it.userId > cId) }
        } else {
            sorted
        }

        filtered.take(clampedLimit)
    }

    /**
     * Backward-compatible overload cho callers cũ chỉ truyền (callerUserId, limit).
     */
    open fun getSuggestions(callerUserId: String, limit: Int): List<MatchingSuggestionDto> {
        return getSuggestions(callerUserId, limit, null)
    }

    /**
     * Gợi ý một chiều khi cạn gợi ý hai chiều (FR-MATCH-4 / DEC-028).
     * Xếp hạng riêng theo PotentialScore (không dùng điểm 2 chiều).
     */
    open fun getPotentialSuggestions(
        callerUserId: String,
        limit: Int = 20,
        cursor: String? = null
    ): List<PotentialMatchDto> = transaction {
        val clampedLimit = limit.coerceIn(1, 50)

        // 1. Lấy profile và skills của caller
        val callerProfileRow = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq callerUserId }
            .firstOrNull() ?: return@transaction emptyList()

        val callerSkills = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { UserSkillsTable.userId eq callerUserId }
            .map {
                UserSkillData(
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel]
                )
            }

        val callerHaveSkills = callerSkills.filter { it.type.equals("HAVE", ignoreCase = true) }
        val callerWantSkills = callerSkills.filter { it.type.equals("WANT", ignoreCase = true) }
        val callerWantIds = callerWantSkills.map { it.skillId }.toSet()
        val callerHaveIds = callerHaveSkills.map { it.skillId }.toSet()

        if (callerHaveSkills.isEmpty()) {
            return@transaction emptyList()
        }

        val callerProfile = UserMatchingProfile(
            userId = callerUserId,
            fullName = callerProfileRow[ProfilesTable.fullName],
            avatarUrl = callerProfileRow[ProfilesTable.avatarUrl],
            city = callerProfileRow[ProfilesTable.city],
            availability = callerProfileRow[ProfilesTable.availability] ?: emptyList(),
            skills = callerSkills
        )

        // 2. Tìm ứng viên cần kỹ năng caller có (WANT in callerHaveIds) và loại trừ quan hệ
        val cooldownCutoff = LocalDateTime.now().minusDays(SUGGESTION_COOLDOWN_DAYS)
        val exclusionCondition = notExists(
            ExchangeRequestsTable.select(intLiteral(1)).where {
                (
                    ((ExchangeRequestsTable.senderId eq callerUserId) and (ExchangeRequestsTable.receiverId eq UserSkillsTable.userId)) or
                    ((ExchangeRequestsTable.receiverId eq callerUserId) and (ExchangeRequestsTable.senderId eq UserSkillsTable.userId))
                ) and (
                    (ExchangeRequestsTable.status inList listOf(
                        ExchangeStatus.PENDING.name,
                        ExchangeStatus.ACCEPTED.name,
                        ExchangeStatus.COMPLETED.name
                    )) or
                    (
                        (ExchangeRequestsTable.status inList listOf(
                            ExchangeStatus.REJECTED.name,
                            ExchangeStatus.CANCELLED.name
                        )) and (
                            (ExchangeRequestsTable.resolvedAt.isNotNull() and (ExchangeRequestsTable.resolvedAt greater cooldownCutoff)) or
                            (ExchangeRequestsTable.resolvedAt.isNull() and (ExchangeRequestsTable.updatedAt greater cooldownCutoff))
                        )
                    )
                )
            }
        )

        val candidateOp: Op<Boolean> = (UserSkillsTable.type eq "WANT") and (UserSkillsTable.skillId inList callerHaveIds.toList())

        val candidateUserIds = UserSkillsTable
            .select(UserSkillsTable.userId)
            .where { (UserSkillsTable.userId neq callerUserId) and candidateOp and exclusionCondition }
            .withDistinct()
            .map { it[UserSkillsTable.userId] }

        if (candidateUserIds.isEmpty()) {
            return@transaction emptyList()
        }

        // 3. Lấy profile và skills của candidate
        val candidateProfileRows = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList candidateUserIds }
            .associateBy { it[ProfilesTable.userId] }

        val candidateSkillsMap = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { UserSkillsTable.userId inList candidateUserIds }
            .map {
                it[UserSkillsTable.userId] to UserSkillData(
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel]
                )
            }
            .groupBy({ it.first }, { it.second })

        // 4. Lọc và tính điểm PotentialScore riêng cho từng ứng viên 1-chiều
        val potentials = candidateUserIds.mapNotNull { candidateId ->
            val pRow = candidateProfileRows[candidateId] ?: return@mapNotNull null
            val candSkills = candidateSkillsMap[candidateId] ?: emptyList()
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
            val candAvail = pRow[ProfilesTable.availability] ?: emptyList()
            val sHaveLevel = bestTeach.proficiencyLevel / 5.0
            val sSched = MatchingCalculator.calculateAvailabilityOverlap(callerProfile.availability, candAvail)
            val sCity = MatchingCalculator.calculateLocationBonus(callerProfile.city, pRow[ProfilesTable.city])
            val sRep = 0.6 // Điểm uy tín chuẩn hóa mặc định cho đối tác mới

            val rawScore = (0.35 * sHaveLevel) + (0.30 * sSched) + (0.20 * sCity) + (0.15 * sRep)
            val potentialScore = (rawScore * 100).roundToInt().coerceIn(0, 100)

            PotentialMatchDto(
                userId = candidateId,
                fullName = pRow[ProfilesTable.fullName],
                avatarUrl = pRow[ProfilesTable.avatarUrl],
                city = pRow[ProfilesTable.city],
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

        // 5. Sắp xếp tất định theo (potentialScore DESC, userId ASC)
        val sorted = potentials.sortedWith(
            compareByDescending<PotentialMatchDto> { it.potentialScore }
                .thenBy { it.userId }
        )

        // 6. Phân trang cursor
        val cursorParsed = cursor?.let { decodeCursor(it) }
        val filtered = if (cursorParsed != null) {
            val (cScore, cId) = cursorParsed
            sorted.filter { it.potentialScore < cScore || (it.potentialScore == cScore && it.userId > cId) }
        } else {
            sorted
        }

        filtered.take(clampedLimit)
    }

    /**
     * Lấy điểm tương thích chi tiết giữa callerUserId và targetUserId.
     * Trả về null nếu targetUserId trùng callerUserId, hoặc không tìm thấy user,
     * hoặc SkillMatch = 0.0.
     */
    open fun getScore(callerUserId: String, targetUserId: String): MatchingScoreResponse? = transaction {
        if (callerUserId == targetUserId) return@transaction null

        val callerRow = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq callerUserId }
            .firstOrNull() ?: return@transaction null

        val targetRow = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq targetUserId }
            .firstOrNull() ?: return@transaction null

        val userIds = listOf(callerUserId, targetUserId)
        val skillsMap = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { UserSkillsTable.userId inList userIds }
            .map {
                it[UserSkillsTable.userId] to UserSkillData(
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel]
                )
            }
            .groupBy({ it.first }, { it.second })

        val callerProfile = UserMatchingProfile(
            userId = callerUserId,
            fullName = callerRow[ProfilesTable.fullName],
            avatarUrl = callerProfileRowOrNull(callerRow),
            city = callerRow[ProfilesTable.city],
            availability = callerRow[ProfilesTable.availability] ?: emptyList(),
            skills = skillsMap[callerUserId] ?: emptyList()
        )

        val targetProfile = UserMatchingProfile(
            userId = targetUserId,
            fullName = targetRow[ProfilesTable.fullName],
            avatarUrl = callerProfileRowOrNull(targetRow),
            city = targetRow[ProfilesTable.city],
            availability = targetRow[ProfilesTable.availability] ?: emptyList(),
            skills = skillsMap[targetUserId] ?: emptyList()
        )

        MatchingCalculator.calculate(callerProfile, targetProfile)
    }

    private fun callerProfileRowOrNull(row: ResultRow): String? {
        return row[ProfilesTable.avatarUrl]
    }

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
}
