package com.skillexchange.app.domain.model

data class UserDiscovery(
    val userId: String,
    val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    val avatarUrl: String? = null,
    val skills: List<UserSkill> = emptyList()
)
