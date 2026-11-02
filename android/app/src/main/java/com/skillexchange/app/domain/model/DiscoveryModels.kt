package com.skillexchange.app.domain.model

data class UserDiscovery(
    val userId: String,
    val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    val avatarUrl: String? = null,
    val skills: List<UserSkill> = emptyList(),
    val matchPercentage: Int? = null,
    val availablePairsCount: Int? = null,
    val relationshipStatus: String? = null, // "ACTIVE", "COMPLETED", null
    val completedExchangeCount: Int = 0
)

data class PotentialMatchSkill(
    val skillId: Int,
    val skillName: String,
    val proficiencyLevel: Int
)

data class PotentialMatch(
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null,
    val canTeachSkill: PotentialMatchSkill,
    val suggestedWantSkill: PotentialMatchSkill,
    val potentialScore: Int
)
