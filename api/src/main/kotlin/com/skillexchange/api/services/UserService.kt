package com.skillexchange.api.services

import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.skill.UserSkillDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class UserSearchDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val skills: List<UserSkillDto> = emptyList()
)

@Serializable
data class UserSearchResultDto(
    val items: List<UserSearchDto>,
    val nextCursor: String? = null
)

/**
 * Exposed function đại diện cho hàm public.f_unaccent(text) trên PostgreSQL (DEC-013).
 */
fun unaccent(expr: Expression<*>): Expression<String> =
    CustomFunction("public.f_unaccent", VarCharColumnType(), expr)

/**
 * Operator ILIKE cho case-insensitive pattern matching trong PostgreSQL.
 */
class ILikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun Expression<*>.ilike(expr2: Expression<*>): Op<Boolean> = ILikeOp(this, expr2)

class UserService {

    /**
     * Tìm kiếm users nâng cao với cursor pagination, đa danh mục (OR), lọc khoảng trình độ,
     * tìm kiếm mờ không dấu (DEC-013 unaccent + pg_trgm ILIKE) và loại trừ chính người gọi.
     */
    fun searchUsersWithCursor(
        query: String? = null,
        categoryIds: List<Int> = emptyList(),
        city: String? = null,
        minProficiency: Int? = null,
        maxProficiency: Int? = null,
        type: String? = null,
        limit: Int = 20,
        lastId: String? = null,
        offset: Int = 0,
        excludeUserId: String? = null
    ): UserSearchResultDto = transaction {

        val clampedLimit = limit.coerceIn(1, 50)
        val fetchLimit = clampedLimit + 1

        // Bước 1: Lấy user_ids phù hợp từ join Profiles + UserSkills + Skills + Categories
        val joinedQuery = ProfilesTable
            .join(UserSkillsTable, JoinType.LEFT, onColumn = null, additionalConstraint = { ProfilesTable.userId eq UserSkillsTable.userId })
            .join(SkillsTable, JoinType.LEFT, onColumn = null, additionalConstraint = { UserSkillsTable.skillId eq SkillsTable.id })
            .join(SkillCategoriesTable, JoinType.LEFT, onColumn = null, additionalConstraint = { SkillsTable.categoryId eq SkillCategoriesTable.id })
            .select(ProfilesTable.userId)
            .withDistinct()
            .apply {
                // Loại trừ chính người gọi (nếu đã xác thực)
                excludeUserId?.takeIf { it.isNotBlank() }?.let { uid ->
                    andWhere { ProfilesTable.userId neq uid }
                }

                // Filter theo query text: dùng unaccent + pg_trgm ILIKE (DEC-013)
                query?.takeIf { it.isNotBlank() }?.let { q ->
                    val unaccentPattern = unaccent(stringLiteral("%$q%"))
                    andWhere {
                        (unaccent(ProfilesTable.fullName) ilike unaccentPattern) or
                        (unaccent(ProfilesTable.city) ilike unaccentPattern) or
                        (unaccent(SkillsTable.name) ilike unaccentPattern)
                    }
                }

                // Filter theo categoryIds (OR logic per DEC-014)
                if (categoryIds.isNotEmpty()) {
                    andWhere { SkillCategoriesTable.id inList categoryIds }
                }

                // Filter theo city: không dấu, case-insensitive
                city?.takeIf { it.isNotBlank() }?.let { c ->
                    val unaccentCity = unaccent(stringLiteral("%$c%"))
                    andWhere { unaccent(ProfilesTable.city) ilike unaccentCity }
                }

                // Filter theo proficiency level range
                minProficiency?.let { minLevel ->
                    andWhere { UserSkillsTable.proficiencyLevel greaterEq minLevel }
                }
                maxProficiency?.let { maxLevel ->
                    andWhere { UserSkillsTable.proficiencyLevel lessEq maxLevel }
                }

                // Filter theo type (HAVE/WANT)
                type?.takeIf { it.isNotBlank() }?.let { t ->
                    andWhere { UserSkillsTable.type eq t }
                }

                // Cursor pagination: lấy các bản ghi có userId lớn hơn lastId
                if (!lastId.isNullOrBlank()) {
                    andWhere { ProfilesTable.userId greater lastId }
                }
            }
            .orderBy(ProfilesTable.userId to SortOrder.ASC)
            .apply {
                if (lastId.isNullOrBlank() && offset > 0) {
                    limit(fetchLimit).offset(offset.toLong())
                } else {
                    limit(fetchLimit)
                }
            }

        val userIds = joinedQuery.map { it[ProfilesTable.userId] }
        if (userIds.isEmpty()) {
            return@transaction UserSearchResultDto(items = emptyList(), nextCursor = null)
        }

        val hasMore = userIds.size > clampedLimit
        val pageUserIds = if (hasMore) userIds.take(clampedLimit) else userIds
        val nextCursor = if (hasMore) pageUserIds.last() else null

        // Bước 2: Lấy profile của từng user
        val profiles = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList pageUserIds }
            .associate { row ->
                row[ProfilesTable.userId] to UserSearchDto(
                    userId    = row[ProfilesTable.userId],
                    fullName  = row[ProfilesTable.fullName],
                    bio       = row[ProfilesTable.bio],
                    city      = row[ProfilesTable.city],
                    avatarUrl = row[ProfilesTable.avatarUrl]
                )
            }

        // Bước 3: Lấy skills của tất cả users trang hiện tại
        val skillsMap = (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .selectAll()
            .where { UserSkillsTable.userId inList pageUserIds }
            .groupBy({ it[UserSkillsTable.userId] }) { row ->
                UserSkillDto(
                    id               = row[UserSkillsTable.id].toString(),
                    skillId          = row[SkillsTable.id],
                    skillName        = row[SkillsTable.name],
                    categoryName     = row[SkillCategoriesTable.name],
                    type             = row[UserSkillsTable.type],
                    proficiencyLevel = row[UserSkillsTable.proficiencyLevel],
                    note             = row[UserSkillsTable.note]
                )
            }

        // Bước 4: Ghép lại theo đúng thứ tự của pageUserIds
        val items = pageUserIds.mapNotNull { uid ->
            profiles[uid]?.copy(skills = skillsMap[uid] ?: emptyList())
        }

        UserSearchResultDto(items = items, nextCursor = nextCursor)
    }

    /**
     * Backward-compatible overload trả về List<UserSearchDto>.
     */
    fun searchUsers(
        query: String? = null,
        categoryId: Int? = null,
        city: String? = null,
        minProficiency: Int? = null,
        type: String? = null,
        limit: Int = 20,
        offset: Int = 0,
        excludeUserId: String? = null
    ): List<UserSearchDto> {
        return searchUsersWithCursor(
            query = query,
            categoryIds = if (categoryId != null) listOf(categoryId) else emptyList(),
            city = city,
            minProficiency = minProficiency,
            maxProficiency = null,
            type = type,
            limit = limit,
            lastId = null,
            offset = offset,
            excludeUserId = excludeUserId
        ).items
    }
}
