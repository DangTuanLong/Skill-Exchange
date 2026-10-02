$modelsCode = @"
package com.skillexchange.api.models.skill

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillCategoryDto(
    val id: Int,
    val name: String,
    val icon: String,
    val description: String? = null,
    val skills: List<SkillDto> = emptyList()
)

@Serializable
data class SkillDto(
    val id: Int,
    @SerialName("category_id") val categoryId: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class UserSkillDto(
    val id: String? = null,
    @SerialName("skill_id") val skillId: Int,
    @SerialName("skill_name") val skillName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val type: String,               // "HAVE" | "WANT"
    @SerialName("proficiency_level") val proficiencyLevel: Int = 1,
    val note: String? = null
)

@Serializable
data class AddUserSkillRequest(
    @SerialName("skill_id") val skillId: Int,
    val type: String,
    @SerialName("proficiency_level") val proficiencyLevel: Int = 1,
    val note: String? = null
)

@Serializable
data class UserDiscoveryDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val skills: List<UserSkillDto> = emptyList()
)
"@

[System.IO.File]::WriteAllText('$PSScriptRoot\..\api\src\main\kotlin\com\skillexchange\api\models\skill\SkillModels.kt', $modelsCode, [System.Text.Encoding]::UTF8)

$serviceCode = @"
package com.skillexchange.api.services

import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.skill.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class SkillService {

    // ── Categories + Skills ───────────────────────────────────────────
    fun getCategories(): List<SkillCategoryDto> = transaction {
        val skills = SkillsTable.selectAll().map {
            SkillDto(
                id = it[SkillsTable.id],
                categoryId = it[SkillsTable.categoryId],
                name = it[SkillsTable.name],
                description = it[SkillsTable.description]
            )
        }.groupBy { it.categoryId }

        SkillCategoriesTable.selectAll()
            .orderBy(SkillCategoriesTable.id)
            .map {
                SkillCategoryDto(
                    id = it[SkillCategoriesTable.id],
                    name = it[SkillCategoriesTable.name],
                    icon = it[SkillCategoriesTable.icon],
                    description = it[SkillCategoriesTable.description],
                    skills = skills[it[SkillCategoriesTable.id]] ?: emptyList()
                )
            }
    }

    fun getSkillsByCategory(categoryId: Int): List<SkillDto> = transaction {
        SkillsTable.selectAll()
            .where { SkillsTable.categoryId eq categoryId }
            .map {
                SkillDto(
                    id = it[SkillsTable.id],
                    categoryId = it[SkillsTable.categoryId],
                    name = it[SkillsTable.name],
                    description = it[SkillsTable.description]
                )
            }
    }

    // ── User Skills ───────────────────────────────────────────────────
    fun getUserSkills(userId: String): List<UserSkillDto> = transaction {
        (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .selectAll()
            .where { UserSkillsTable.userId eq userId }
            .map {
                UserSkillDto(
                    id = it[UserSkillsTable.id].toString(),
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    categoryName = it[SkillCategoriesTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel],
                    note = it[UserSkillsTable.note]
                )
            }
    }

    fun addUserSkill(userId: String, req: AddUserSkillRequest): UserSkillDto = transaction {
        val existing = UserSkillsTable.selectAll()
            .where {
                (UserSkillsTable.userId eq userId) and
                (UserSkillsTable.skillId eq req.skillId) and
                (UserSkillsTable.type eq req.type)
            }.firstOrNull()

        if (existing != null) {
            UserSkillsTable.update({
                (UserSkillsTable.userId eq userId) and
                (UserSkillsTable.skillId eq req.skillId) and
                (UserSkillsTable.type eq req.type)
            }) {
                it[proficiencyLevel] = req.proficiencyLevel
                it[note] = req.note
            }
        } else {
            UserSkillsTable.insert {
                it[UserSkillsTable.userId]         = userId
                it[skillId]                        = req.skillId
                it[type]                           = req.type
                it[proficiencyLevel]               = req.proficiencyLevel
                it[note]                           = req.note
                it[createdAt]                      = LocalDateTime.now()
            }
        }

        val skill = SkillsTable.selectAll().where { SkillsTable.id eq req.skillId }.first()
        val cat   = SkillCategoriesTable.selectAll()
            .where { SkillCategoriesTable.id eq skill[SkillsTable.categoryId] }.first()

        UserSkillDto(
            skillId = req.skillId,
            skillName = skill[SkillsTable.name],
            categoryName = cat[SkillCategoriesTable.name],
            type = req.type,
            proficiencyLevel = req.proficiencyLevel,
            note = req.note
        )
    }

    fun removeUserSkill(userId: String, userSkillId: String): Boolean = transaction {
        val deleted = UserSkillsTable.deleteWhere {
            (UserSkillsTable.userId eq userId) and
            (UserSkillsTable.id eq java.util.UUID.fromString(userSkillId))
        }
        deleted > 0
    }

    // ── Search & Discovery ────────────────────────────────────────────
    fun searchDiscovery(
        query: String?,
        categoryId: Int?,
        city: String?,
        minProficiency: Int?,
        type: String?,
        limit: Int = 20,
        offset: Int = 0
    ): List<UserDiscoveryDto> = transaction {
        var op: Op<Boolean> = Op.TRUE

        if (!query.isNullOrBlank()) {
            val qStr = "%`$query%"
            val queryOp = (ProfilesTable.fullName.lowerCase() like qStr.lowercase()) or
                          (ProfilesTable.bio.lowerCase() like qStr.lowercase()) or
                          (SkillsTable.name.lowerCase() like qStr.lowercase()) or
                          (SkillCategoriesTable.name.lowerCase() like qStr.lowercase())
            op = op and queryOp
        }

        if (categoryId != null) {
            op = op and (SkillsTable.categoryId eq categoryId)
        }

        if (!city.isNullOrBlank()) {
            val cStr = "%`$city%"
            op = op and (ProfilesTable.city.lowerCase() like cStr.lowercase())
        }

        if (minProficiency != null) {
            op = op and (UserSkillsTable.proficiencyLevel greaterEq minProficiency)
        }

        if (!type.isNullOrBlank()) {
            op = op and (UserSkillsTable.type eq type.uppercase())
        }

        val matchedUserIds = (ProfilesTable innerJoin UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .select(ProfilesTable.userId)
            .where { op }
            .withDistinct(true)
            .limit(limit, offset = offset.toLong())
            .map { it[ProfilesTable.userId] }

        if (matchedUserIds.isEmpty()) return@transaction emptyList()

        val profiles = ProfilesTable.selectAll().where { ProfilesTable.userId inList matchedUserIds }
            .associateBy { it[ProfilesTable.userId] }

        val userSkillsMap = (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .selectAll().where { UserSkillsTable.userId inList matchedUserIds }
            .map {
                it[UserSkillsTable.userId] to UserSkillDto(
                    id = it[UserSkillsTable.id].toString(),
                    skillId = it[SkillsTable.id],
                    skillName = it[SkillsTable.name],
                    categoryName = it[SkillCategoriesTable.name],
                    type = it[UserSkillsTable.type],
                    proficiencyLevel = it[UserSkillsTable.proficiencyLevel],
                    note = it[UserSkillsTable.note]
                )
            }
            .groupBy({ it.first }, { it.second })

        matchedUserIds.mapNotNull { userId ->
            val p = profiles[userId] ?: return@mapNotNull null
            UserDiscoveryDto(
                userId = userId,
                fullName = p[ProfilesTable.fullName],
                bio = p[ProfilesTable.bio],
                city = p[ProfilesTable.city],
                avatarUrl = p[ProfilesTable.avatarUrl],
                skills = userSkillsMap[userId] ?: emptyList()
            )
        }
    }

    // ── Seed Data ─────────────────────────────────────────────────────
    fun seedIfEmpty() = transaction {
        if (SkillCategoriesTable.selectAll().count() > 0) return@transaction

        val categories = listOf(
            Triple(1, "Lập trình", "💻"),
            Triple(2, "Ngoại ngữ", "🌍"),
            Triple(3, "Âm nhạc", "🎵"),
            Triple(4, "Thiết kế", "🎨"),
            Triple(5, "Nấu ăn", "🍳"),
            Triple(6, "Thể thao", "⚽"),
            Triple(7, "Kinh doanh", "💼"),
            Triple(8, "Nhiếp ảnh", "📷"),
            Triple(9, "Viết lách", "✍️"),
            Triple(10, "Khác", "🌟")
        )

        val skillsByCat = mapOf(
            1 to listOf("Android (Kotlin)", "iOS (Swift)", "Web (React)", "Web (Vue)", "Python", "Java", "Flutter", "Node.js", "SQL", "AI/ML"),
            2 to listOf("Tiếng Anh", "Tiếng Nhật", "Tiếng Hàn", "Tiếng Trung", "Tiếng Pháp", "Tiếng Đức", "Tiếng Tây Ban Nha"),
            3 to listOf("Guitar", "Piano", "Ukulele", "Violin", "Trống", "Hát", "Sáng tác nhạc", "Beat making"),
            4 to listOf("Figma / UI Design", "Adobe Photoshop", "Illustrator", "Video Editing", "3D Modeling", "Motion Graphics"),
            5 to listOf("Món Việt", "Bánh ngọt", "Nấu chay", "Món Nhật", "Món Ý", "BBQ / Nướng"),
            6 to listOf("Bóng đá", "Cầu lông", "Gym / Fitness", "Yoga", "Bơi lội", "Cờ vua", "Tennis"),
            7 to listOf("Marketing Digital", "Bán hàng / Sales", "Kế toán", "Excel nâng cao", "Quản lý dự án", "Khởi nghiệp"),
            8 to listOf("Chụp ảnh chân dung", "Chụp sản phẩm", "Lightroom", "Quay phim", "Drone"),
            9 to listOf("Viết content", "Copywriting", "Viết truyện", "Dịch thuật", "Báo chí"),
            10 to listOf("Dạy học", "Tư vấn", "Thủ công / Handmade", "Làm vườn", "Mẹo sống xanh")
        )

        categories.forEach { (id, name, icon) ->
            SkillCategoriesTable.insert {
                it[SkillCategoriesTable.id]   = id
                it[SkillCategoriesTable.name] = name
                it[SkillCategoriesTable.icon] = icon
            }
            skillsByCat[id]?.forEach { skillName ->
                SkillsTable.insert {
                    it[categoryId] = id
                    it[SkillsTable.name] = skillName
                }
            }
        }
    }
}
"@

[System.IO.File]::WriteAllText('$PSScriptRoot\..\api\src\main\kotlin\com\skillexchange\api\services\SkillService.kt', $serviceCode, [System.Text.Encoding]::UTF8)
Write-Host "Updated SkillService.kt with fixed Exposed ORM syntax"
