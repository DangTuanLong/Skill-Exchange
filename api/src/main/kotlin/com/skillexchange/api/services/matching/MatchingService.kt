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

        val candidateProfiles = candidateUserIds.mapNotNull { candidateId ->
            val pRow = candidateProfileRows[candidateId] ?: return@mapNotNull null
            UserMatchingProfile(
                userId = candidateId,
                fullName = pRow[ProfilesTable.fullName],
                avatarUrl = pRow[ProfilesTable.avatarUrl],
                city = pRow[ProfilesTable.city],
                availability = pRow[ProfilesTable.availability] ?: emptyList(),
                skills = candidateSkillsMap[candidateId] ?: emptyList()
            )
        }

        MatchingLogicHelper.rankSuggestions(callerProfile, candidateProfiles, cursor, clampedLimit)
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

        val candidateProfiles = candidateUserIds.mapNotNull { candidateId ->
            val pRow = candidateProfileRows[candidateId] ?: return@mapNotNull null
            UserMatchingProfile(
                userId = candidateId,
                fullName = pRow[ProfilesTable.fullName],
                avatarUrl = pRow[ProfilesTable.avatarUrl],
                city = pRow[ProfilesTable.city],
                availability = pRow[ProfilesTable.availability] ?: emptyList(),
                skills = candidateSkillsMap[candidateId] ?: emptyList()
            )
        }

        MatchingLogicHelper.rankPotentialMatches(callerProfile, candidateProfiles, cursor, clampedLimit)
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

    fun encodeCursor(score: Int, userId: String): String = MatchingLogicHelper.encodeCursor(score, userId)

    fun decodeCursor(cursor: String): Pair<Int, String>? = MatchingLogicHelper.decodeCursor(cursor)
}
