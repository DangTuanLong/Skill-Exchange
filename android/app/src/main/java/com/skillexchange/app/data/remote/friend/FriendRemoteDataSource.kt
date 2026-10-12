package com.skillexchange.app.data.remote.friend

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.ApiErrorDto
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.data.remote.auth.ApiWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class FriendRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun sendFriendRequest(friendId: String): FriendshipDto {
        val response = client.post("${Constants.BASE_URL}/api/friendships") {
            contentType(ContentType.Application.Json)
            setBody(CreateFriendshipRequestDto(friendId = friendId))
        }
        return handleResponse(response)
    }

    suspend fun acceptFriendRequest(friendshipId: String): FriendshipDto {
        val response = client.put("${Constants.BASE_URL}/api/friendships/$friendshipId/accept")
        return handleResponse(response)
    }

    suspend fun rejectFriendRequest(friendshipId: String): FriendshipDto {
        val response = client.put("${Constants.BASE_URL}/api/friendships/$friendshipId/reject")
        return handleResponse(response)
    }

    suspend fun removeFriendship(friendshipId: String): Boolean {
        val response = client.delete("${Constants.BASE_URL}/api/friendships/$friendshipId")
        if (response.status.isSuccess()) {
            return true
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun getFriends(): List<FriendDto> {
        val response = client.get("${Constants.BASE_URL}/api/friendships/me")
        return handleResponse(response)
    }

    suspend fun getPendingRequests(): List<FriendDto> {
        val response = client.get("${Constants.BASE_URL}/api/friendships/pending")
        return handleResponse(response)
    }

    suspend fun getFriendshipStatus(userId: String): FriendshipStatusDto {
        val response = client.get("${Constants.BASE_URL}/api/friendships/status/$userId")
        return handleResponse(response)
    }
}
