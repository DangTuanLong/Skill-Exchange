package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.skill.AddUserSkillDto
import com.skillexchange.app.data.remote.skill.SkillRemoteDataSource
import com.skillexchange.app.domain.model.Skill
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SkillRepositoryImpl(
    private val remoteDataSource: SkillRemoteDataSource,
    private val tokenManager: TokenManager,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : ISkillRepository {

    companion object {
        private const val CATEGORIES_CACHE_TTL_MS = 5 * 60 * 1000L // 5 phút
    }

    private val cacheMutex = Mutex()
    @Volatile
    private var cachedCategories: List<SkillCategory>? = null
    @Volatile
    private var cacheTimestampMs: Long = 0L

    override suspend fun getCategories(): Result<List<SkillCategory>> = cacheMutex.withLock {
        val now = nowProvider()
        val cached = cachedCategories
        if (cached != null && (now - cacheTimestampMs) < CATEGORIES_CACHE_TTL_MS) {
            return@withLock Result.success(cached)
        }
        runCatching {
            remoteDataSource.getCategories().data.map { cat ->
                SkillCategory(
                    id = cat.id,
                    name = cat.name,
                    icon = cat.icon,
                    skills = cat.skills.map { Skill(it.id, it.categoryId, it.name) }
                )
            }
        }.onSuccess { categories ->
            cachedCategories = categories
            cacheTimestampMs = nowProvider()
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
            id = data.id ?: "",
            skillId = data.skillId,
            skillName = data.skillName ?: "",
            categoryName = data.categoryName ?: "",
            type = if (data.type == "HAVE") SkillType.HAVE else SkillType.WANT,
            proficiencyLevel = data.proficiencyLevel
        )
    }

    override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        val res = remoteDataSource.removeUserSkill(token, userSkillId)
        if (!res.success) {
            error(res.message ?: "Không thể xóa kỹ năng")
        }
        true
    }
}
