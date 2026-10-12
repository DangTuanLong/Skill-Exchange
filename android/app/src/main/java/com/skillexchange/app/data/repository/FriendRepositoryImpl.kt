package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.friend.FriendDto
import com.skillexchange.app.data.remote.friend.FriendRemoteDataSource
import com.skillexchange.app.data.remote.friend.FriendshipStatusDto
import com.skillexchange.app.domain.model.friend.Friend
import com.skillexchange.app.domain.model.friend.FriendshipRelation
import com.skillexchange.app.domain.model.friend.FriendshipStatusType
import com.skillexchange.app.domain.repository.IFriendRepository

class FriendRepositoryImpl(
    private val remoteDataSource: FriendRemoteDataSource
) : IFriendRepository {

    override suspend fun sendFriendRequest(friendId: String): Result<Unit> = runCatching {
        remoteDataSource.sendFriendRequest(friendId)
        Unit
    }

    override suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        remoteDataSource.acceptFriendRequest(friendshipId)
        Unit
    }

    override suspend fun rejectFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        remoteDataSource.rejectFriendRequest(friendshipId)
        Unit
    }

    override suspend fun removeFriendship(friendshipId: String): Result<Unit> = runCatching {
        remoteDataSource.removeFriendship(friendshipId)
        Unit
    }

    override suspend fun getFriends(): Result<List<Friend>> = runCatching {
        val dtos = remoteDataSource.getFriends()
        dtos.map { it.toDomain() }
    }

    override suspend fun getPendingRequests(): Result<List<Friend>> = runCatching {
        val dtos = remoteDataSource.getPendingRequests()
        dtos.map { it.toDomain() }
    }

    override suspend fun getFriendshipStatus(userId: String): Result<FriendshipRelation> = runCatching {
        val dto = remoteDataSource.getFriendshipStatus(userId)
        dto.toDomain()
    }

    private fun FriendDto.toDomain() = Friend(
        friendshipId = friendshipId,
        userId = userId,
        fullName = fullName,
        avatarUrl = avatarUrl,
        city = city,
        bio = bio,
        status = status,
        createdAt = createdAt
    )

    private fun FriendshipStatusDto.toDomain(): FriendshipRelation {
        val type = when (status) {
            "ACCEPTED" -> FriendshipStatusType.ACCEPTED
            "PENDING_SENT" -> FriendshipStatusType.PENDING_SENT
            "PENDING_RECEIVED" -> FriendshipStatusType.PENDING_RECEIVED
            "REJECTED" -> FriendshipStatusType.REJECTED
            else -> FriendshipStatusType.NONE
        }
        return FriendshipRelation(status = type, friendshipId = friendshipId)
    }
}
