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