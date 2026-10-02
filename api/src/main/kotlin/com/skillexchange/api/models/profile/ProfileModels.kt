package com.skillexchange.api.models.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
