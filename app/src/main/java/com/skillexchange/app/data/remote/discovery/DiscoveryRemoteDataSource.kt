package com.skillexchange.app.data.remote.discovery

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.data.remote.skill.UserSkillDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDiscoveryDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val skills: List<UserSkillDto> = emptyList()
)

@Serializable
data class DiscoverySearchResponseDto(
    val success: Boolean,
    val data: List<UserDiscoveryDto> = emptyList(),
    val message: String? = null
)

class DiscoveryRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun searchUsers(
        query: String?,
        categoryId: Int?,
        city: String?,
        minProficiency: Int?,
        type: String?,
        limit: Int = 20,
        offset: Int = 0
    ): DiscoverySearchResponseDto = httpClient.get("${Constants.BASE_URL}/api/skills/search") {
        query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
        categoryId?.let { parameter("category", it) }
        city?.takeIf { it.isNotBlank() }?.let { parameter("city", it) }
        minProficiency?.let { parameter("minProficiency", it) }
        type?.takeIf { it.isNotBlank() }?.let { parameter("type", it) }
        parameter("limit", limit)
        parameter("offset", offset)
    }.body()
}
