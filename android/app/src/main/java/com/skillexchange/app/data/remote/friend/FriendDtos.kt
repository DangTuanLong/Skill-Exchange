package com.skillexchange.app.data.remote.friend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateFriendshipRequestDto(
    @SerialName("friend_id") val friendId: String
)

@Serializable
data class FriendshipDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class FriendDto(
    @SerialName("friendship_id") val friendshipId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val city: String? = null,
    val bio: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class FriendshipStatusDto(
    val status: String,
    @SerialName("friendship_id") val friendshipId: String? = null
)
