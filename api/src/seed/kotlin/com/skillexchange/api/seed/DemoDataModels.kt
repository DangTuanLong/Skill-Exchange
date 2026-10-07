package com.skillexchange.api.seed

import com.skillexchange.api.models.profile.AvailabilityWindowDto

/**
 * Data models đại diện cho người dùng demo và kỹ năng được sinh ra trong TASK-013.
 */
data class SeedSkill(
    val name: String,
    val level: Int,
    val type: String // "HAVE" hoặc "WANT"
)

data class SeedUser(
    val email: String,
    val fullName: String,
    val city: String,
    val bio: String,
    val availability: List<AvailabilityWindowDto>,
    val skills: List<SeedSkill>,
    val isScenarioUser: Boolean = false
)
