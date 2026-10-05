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

class UserService {

    /**
     * Tìm kiếm users theo query, category, city, minProficiency, type.
     * Trả về list UserSearchDto kèm skills của mỗi user.
     */
    fun searchUsers(
        query: String? = null,
        categoryId: Int? = null,
        city: String? = null,
        minProficiency: Int? = null,
        type: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<UserSearchDto> = transaction {

        // Bước 1: Lấy user_ids phù hợp từ join Profiles + UserSkills + Skills + Categories
        val joinedQuery = ProfilesTable
            .join(UserSkillsTable, JoinType.LEFT, onColumn = null, additionalConstraint = { ProfilesTable.userId eq UserSkillsTable.userId })
            .join(SkillsTable, JoinType.LEFT, onColumn = null, additionalConstraint = { UserSkillsTable.skillId eq SkillsTable.id })
            .join(SkillCategoriesTable, JoinType.LEFT, onColumn = null, additionalConstraint = { SkillsTable.categoryId eq SkillCategoriesTable.id })
            .select(ProfilesTable.userId)
            .withDistinct()
            .apply {
                // Filter theo query (tìm theo tên hoặc thành phố hoặc tên kỹ năng)
                query?.takeIf { it.isNotBlank() }?.let { q ->
                    andWhere {
                        (ProfilesTable.fullName like "%$q%") or
                        (ProfilesTable.city like "%$q%") or
                        (SkillsTable.name like "%$q%")
                    }
                }
                // Filter theo category
                categoryId?.let { catId ->
                    andWhere { SkillCategoriesTable.id eq catId }
                }
                // Filter theo city
                city?.takeIf { it.isNotBlank() }?.let { c ->
                    andWhere { ProfilesTable.city like "%$c%" }
                }
                // Filter theo proficiency
                minProficiency?.let { minLevel ->
                    andWhere { UserSkillsTable.proficiencyLevel greaterEq minLevel }
                }
                // Filter theo type (HAVE/WANT)
                type?.takeIf { it.isNotBlank() }?.let { t ->
                    andWhere { UserSkillsTable.type eq t }
                }
            }
            .limit(limit, offset.toLong())

        val userIds = joinedQuery.map { it[ProfilesTable.userId] }
        if (userIds.isEmpty()) return@transaction emptyList()

        // Bước 2: Lấy profile của từng user
        val profiles = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList userIds }
            .associate { row ->
                row[ProfilesTable.userId] to UserSearchDto(
                    userId    = row[ProfilesTable.userId],
                    fullName  = row[ProfilesTable.fullName],
                    bio       = row[ProfilesTable.bio],
                    city      = row[ProfilesTable.city],
                    avatarUrl = row[ProfilesTable.avatarUrl]
                )
            }

        // Bước 3: Lấy skills của tất cả users tìm được
        val skillsMap = (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .selectAll()
            .where { UserSkillsTable.userId inList userIds }
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

        // Bước 4: Ghép lại
        userIds.mapNotNull { uid ->
            profiles[uid]?.copy(skills = skillsMap[uid] ?: emptyList())
        }
    }
}
