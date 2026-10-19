package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.discovery.DiscoveryRemoteDataSource
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.DiscoverySearchResult
import com.skillexchange.app.domain.repository.IDiscoveryRepository

class DiscoveryRepositoryImpl(
    private val remoteDataSource: DiscoveryRemoteDataSource
) : IDiscoveryRepository {

    override suspend fun searchUsers(
        query: String?,
        categoryIds: List<Int>?,
        city: String?,
        minProficiency: Int?,
        maxProficiency: Int?,
        type: String?,
        limit: Int,
        lastId: String?
    ): Result<DiscoverySearchResult> = runCatching {
        val response = remoteDataSource.searchUsers(
            query = query,
            categoryIds = categoryIds,
            city = city,
            minProficiency = minProficiency,
            maxProficiency = maxProficiency,
            type = type,
            limit = limit,
            lastId = lastId
        )
        if (!response.success) {
            error(response.message ?: "Không thể tìm kiếm")
        }
        val items = response.data?.items ?: emptyList()
        val users = items.map { userDto ->
            UserDiscovery(
                userId = userDto.userId,
                fullName = userDto.fullName,
                bio = userDto.bio,
                city = userDto.city,
                avatarUrl = userDto.avatarUrl,
                skills = userDto.skills.map { sDto ->
                    UserSkill(
                        id = sDto.id ?: "",
                        skillId = sDto.skillId,
                        skillName = sDto.skillName ?: "",
                        categoryName = sDto.categoryName ?: "",
                        type = if (sDto.type == "HAVE") SkillType.HAVE else SkillType.WANT,
                        proficiencyLevel = sDto.proficiencyLevel,
                        note = sDto.note
                    )
                }
            )
        }
        DiscoverySearchResult(
            users = users,
            nextCursor = response.data?.nextCursor
        )
    }

    override suspend fun getMatchingSuggestions(limit: Int): Result<List<UserDiscovery>> = runCatching {
        val response = remoteDataSource.getMatchingSuggestions(limit)
        if (!response.success) {
            error(response.message ?: "Không thể tải gợi ý phù hợp")
        }
        val items = response.data ?: emptyList()
        items.map { dto ->
            val skills = mutableListOf<UserSkill>()
            dto.matchedSkills?.theyTeachYou?.forEach { s ->
                skills.add(
                    UserSkill(
                        id = "matched_have_${s.skillId}",
                        skillId = s.skillId,
                        skillName = s.skillName,
                        categoryName = "",
                        type = SkillType.HAVE,
                        proficiencyLevel = s.teacherLevel
                    )
                )
            }
            dto.matchedSkills?.youTeachThem?.forEach { s ->
                skills.add(
                    UserSkill(
                        id = "matched_want_${s.skillId}",
                        skillId = s.skillId,
                        skillName = s.skillName,
                        categoryName = "",
                        type = SkillType.WANT,
                        proficiencyLevel = s.learnerLevel
                    )
                )
            }
            UserDiscovery(
                userId = dto.userId,
                fullName = dto.fullName,
                avatarUrl = dto.avatarUrl,
                city = dto.city,
                skills = skills,
                matchPercentage = dto.percentage
            )
        }
    }
}
