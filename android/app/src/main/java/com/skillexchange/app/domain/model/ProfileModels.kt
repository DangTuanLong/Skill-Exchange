package com.skillexchange.app.domain.model

data class AvailabilityWindow(
    val day: String,
    val from: String,
    val to: String
)

data class Profile(
    val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val bio: String? = null,
    val city: String? = null,
    val avatarUrl: String? = null,
    val availability: List<AvailabilityWindow> = emptyList()
)


data class SkillCategory(
    val id: Int,
    val name: String,
    val icon: String,
    val skills: List<Skill> = emptyList()
)

data class Skill(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val description: String? = null
)

data class UserSkill(
    val id: String = "",
    val skillId: Int,
    val skillName: String = "",
    val categoryName: String = "",
    val type: SkillType,
    val proficiencyLevel: Int = 1,  // 1-5
    val note: String? = null
)

enum class SkillType { HAVE, WANT }
