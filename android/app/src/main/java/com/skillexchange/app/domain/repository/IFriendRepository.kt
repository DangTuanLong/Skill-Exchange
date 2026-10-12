package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.friend.Friend
import com.skillexchange.app.domain.model.friend.FriendshipRelation

interface IFriendRepository {
    suspend fun sendFriendRequest(friendId: String): Result<Unit>
    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit>
    suspend fun rejectFriendRequest(friendshipId: String): Result<Unit>
    suspend fun removeFriendship(friendshipId: String): Result<Unit>
    suspend fun getFriends(): Result<List<Friend>>
    suspend fun getPendingRequests(): Result<List<Friend>>
    suspend fun getFriendshipStatus(userId: String): Result<FriendshipRelation>
}
