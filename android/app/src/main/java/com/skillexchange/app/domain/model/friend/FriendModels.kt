package com.skillexchange.app.domain.model.friend

enum class FriendshipStatusType {
    NONE,
    PENDING_SENT,
    PENDING_RECEIVED,
    ACCEPTED,
    REJECTED
}

data class FriendshipRelation(
    val status: FriendshipStatusType,
    val friendshipId: String? = null
)

data class Friend(
    val friendshipId: String,
    val userId: String,
    val fullName: String,
    val avatarUrl: String?,
    val city: String?,
    val bio: String?,
    val status: String,
    val createdAt: String
)
