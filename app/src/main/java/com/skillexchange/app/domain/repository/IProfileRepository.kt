package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.UserSkill

interface IProfileRepository {
    suspend fun getMyProfile(): Result<Profile>
    suspend fun getUserProfile(userId: String): Result<Profile>
    suspend fun updateProfile(fullName: String, bio: String?, city: String?, avatarUrl: String?): Result<Profile>
}

interface ISkillRepository {
    suspend fun getCategories(): Result<List<SkillCategory>>
    suspend fun getUserSkills(userId: String): Result<List<UserSkill>>
    suspend fun addUserSkill(skillId: Int, type: String, proficiencyLevel: Int, note: String?): Result<UserSkill>
    suspend fun removeUserSkill(userSkillId: String): Result<Boolean>
}
