package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.skill.AddUserSkillDto
import com.skillexchange.app.data.remote.skill.SkillRemoteDataSource
import com.skillexchange.app.domain.model.Skill
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.ISkillRepository

class SkillRepositoryImpl(
    private val remoteDataSource: SkillRemoteDataSource,
    private val tokenManager: TokenManager
) : ISkillRepository {

    override suspend fun getCategories(): Result<List<SkillCategory>> = runCatching {
        remoteDataSource.getCategories().data.map { cat ->
            SkillCategory(
                id = cat.id,
                name = cat.name,
                icon = cat.icon,
                skills = cat.skills.map { Skill(it.id, it.categoryId, it.name) }
            )
        }
    }

    override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        remoteDataSource.getUserSkills(token, userId).data.map {
            UserSkill(
                id = it.id ?: "",
                skillId = it.skillId,
                skillName = it.skillName ?: "",
                categoryName = it.categoryName ?: "",
                type = if (it.type == "HAVE") SkillType.HAVE else SkillType.WANT,
                proficiencyLevel = it.proficiencyLevel,
                note = it.note
            )
        }
    }

    override suspend fun addUserSkill(
        skillId: Int, type: String, proficiencyLevel: Int, note: String?
    ): Result<UserSkill> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        val resp = remoteDataSource.addUserSkill(token, AddUserSkillDto(skillId, type, proficiencyLevel, note))
        val data = resp.data ?: error(resp.message ?: "Thêm kỹ năng thất bại")
        UserSkill(
            skillId = data.skillId,
            skillName = data.skillName ?: "",
            categoryName = data.categoryName ?: "",
            type = if (data.type == "HAVE") SkillType.HAVE else SkillType.WANT,
            proficiencyLevel = data.proficiencyLevel
        )
    }

    override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        remoteDataSource.removeUserSkill(token, userSkillId)["success"] ?: false
    }
}
