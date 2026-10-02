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

    // â”€â”€ Categories + Skills â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€ User Skills â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€ Search & Discovery â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            val qStr = "%$query%"
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
            val cStr = "%$city%"
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

    // â”€â”€ Seed Data â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun seedIfEmpty() = transaction {
        if (SkillCategoriesTable.selectAll().count() > 0) return@transaction

        val categories = listOf(
            Triple(1, "Láº­p trÃ¬nh", "ðŸ’»"),
            Triple(2, "Ngoáº¡i ngá»¯", "ðŸŒ"),
            Triple(3, "Ã‚m nháº¡c", "ðŸŽµ"),
            Triple(4, "Thiáº¿t káº¿", "ðŸŽ¨"),
            Triple(5, "Náº¥u Äƒn", "ðŸ³"),
            Triple(6, "Thá»ƒ thao", "âš½"),
            Triple(7, "Kinh doanh", "ðŸ’¼"),
            Triple(8, "Nhiáº¿p áº£nh", "ðŸ“·"),
            Triple(9, "Viáº¿t lÃ¡ch", "âœï¸"),
            Triple(10, "KhÃ¡c", "ðŸŒŸ")
        )

        val skillsByCat = mapOf(
            1 to listOf("Android (Kotlin)", "iOS (Swift)", "Web (React)", "Web (Vue)", "Python", "Java", "Flutter", "Node.js", "SQL", "AI/ML"),
            2 to listOf("Tiáº¿ng Anh", "Tiáº¿ng Nháº­t", "Tiáº¿ng HÃ n", "Tiáº¿ng Trung", "Tiáº¿ng PhÃ¡p", "Tiáº¿ng Äá»©c", "Tiáº¿ng TÃ¢y Ban Nha"),
            3 to listOf("Guitar", "Piano", "Ukulele", "Violin", "Trá»‘ng", "HÃ¡t", "SÃ¡ng tÃ¡c nháº¡c", "Beat making"),
            4 to listOf("Figma / UI Design", "Adobe Photoshop", "Illustrator", "Video Editing", "3D Modeling", "Motion Graphics"),
            5 to listOf("MÃ³n Viá»‡t", "BÃ¡nh ngá»t", "Náº¥u chay", "MÃ³n Nháº­t", "MÃ³n Ã", "BBQ / NÆ°á»›ng"),
            6 to listOf("BÃ³ng Ä‘Ã¡", "Cáº§u lÃ´ng", "Gym / Fitness", "Yoga", "BÆ¡i lá»™i", "Cá» vua", "Tennis"),
            7 to listOf("Marketing Digital", "BÃ¡n hÃ ng / Sales", "Káº¿ toÃ¡n", "Excel nÃ¢ng cao", "Quáº£n lÃ½ dá»± Ã¡n", "Khá»Ÿi nghiá»‡p"),
            8 to listOf("Chá»¥p áº£nh chÃ¢n dung", "Chá»¥p sáº£n pháº©m", "Lightroom", "Quay phim", "Drone"),
            9 to listOf("Viáº¿t content", "Copywriting", "Viáº¿t truyá»‡n", "Dá»‹ch thuáº­t", "BÃ¡o chÃ­"),
            10 to listOf("Dáº¡y há»c", "TÆ° váº¥n", "Thá»§ cÃ´ng / Handmade", "LÃ m vÆ°á»n", "Máº¹o sá»‘ng xanh")
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