package com.skillexchange.api.services.matching

import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.matching.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction

open class MatchingService {

    /**
     * Gợi ý đối tác trao đổi kỹ năng cho callerUserId.
     * Trả về danh sách ứng viên sắp xếp theo totalScore giảm dần.
     * Loại bỏ chính người gọi và các ứng viên có SkillMatch = 0.
     */
    open fun getSuggestions(callerUserId: String, limit: Int = 20): List<MatchingSuggestionDto> = transaction {
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

        val candidateUserIds = UserSkillsTable
            .select(UserSkillsTable.userId)
            .where { (UserSkillsTable.userId neq callerUserId) and candidateOp }
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

        // 4. Tính toán điểm cho từng ứng viên
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
                matchedSkills = score.matchedSkills
            )
        }

        // 5. Sắp xếp theo totalScore DESC, percentage DESC, userId ASC
        suggestions.sortedWith(
            compareByDescending<MatchingSuggestionDto> { it.totalScore }
                .thenByDescending { it.percentage }
                .thenBy { it.userId }
        ).take(clampedLimit)
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
            avatarUrl = callerRow[ProfilesTable.avatarUrl],
            city = callerRow[ProfilesTable.city],
            availability = callerRow[ProfilesTable.availability] ?: emptyList(),
            skills = skillsMap[callerUserId] ?: emptyList()
        )

        val targetProfile = UserMatchingProfile(
            userId = targetUserId,
            fullName = targetRow[ProfilesTable.fullName],
            avatarUrl = targetRow[ProfilesTable.avatarUrl],
            city = targetRow[ProfilesTable.city],
            availability = targetRow[ProfilesTable.availability] ?: emptyList(),
            skills = skillsMap[targetUserId] ?: emptyList()
        )

        MatchingCalculator.calculate(callerProfile, targetProfile)
    }
}
