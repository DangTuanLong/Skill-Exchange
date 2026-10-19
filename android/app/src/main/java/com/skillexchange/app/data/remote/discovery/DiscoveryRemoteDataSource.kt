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
data class DiscoverySearchDataDto(
    val items: List<UserDiscoveryDto> = emptyList(),
    val nextCursor: String? = null
)

@Serializable
data class DiscoverySearchResponseDto(
    val success: Boolean,
    val data: DiscoverySearchDataDto? = null,
    val message: String? = null
)

@Serializable
data class MatchedSkillDto(
    @SerialName("skill_id") val skillId: Int,
    @SerialName("skill_name") val skillName: String,
    @SerialName("teacher_level") val teacherLevel: Int = 1,
    @SerialName("learner_level") val learnerLevel: Int = 1
)

@Serializable
data class MatchedSkillsBreakdownDto(
    @SerialName("they_teach_you") val theyTeachYou: List<MatchedSkillDto> = emptyList(),
    @SerialName("you_teach_them") val youTeachThem: List<MatchedSkillDto> = emptyList()
)

@Serializable
data class MatchingSuggestionDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val city: String? = null,
    @SerialName("total_score") val totalScore: Double = 0.0,
    val percentage: Int = 0,
    @SerialName("matched_skills") val matchedSkills: MatchedSkillsBreakdownDto? = null
)

@Serializable
data class MatchingSuggestionsResponseDto(
    val success: Boolean,
    val data: List<MatchingSuggestionDto>? = null,
    val message: String? = null
)

class DiscoveryRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun searchUsers(
        query: String?,
        categoryIds: List<Int>?,
        city: String?,
        minProficiency: Int?,
        maxProficiency: Int? = null,
        type: String?,
        limit: Int = 20,
        lastId: String? = null
    ): DiscoverySearchResponseDto = httpClient.get("${Constants.BASE_URL}/api/users/search") {
        query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
        if (!categoryIds.isNullOrEmpty()) {
            parameter("categoryIds", categoryIds.joinToString(","))
        }
        city?.takeIf { it.isNotBlank() }?.let { parameter("city", it) }
        minProficiency?.let { parameter("minProficiency", it) }
        maxProficiency?.let { parameter("maxProficiency", it) }
        type?.takeIf { it.isNotBlank() }?.let { parameter("type", it) }
        parameter("limit", limit)
        lastId?.takeIf { it.isNotBlank() }?.let { parameter("lastId", it) }
    }.body()

    suspend fun getMatchingSuggestions(limit: Int = 20): MatchingSuggestionsResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/matching/suggestions") {
            parameter("limit", limit)
        }.body()
}
