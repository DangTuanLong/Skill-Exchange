package com.skillexchange.app.data.remote.skill

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillCategoryResponseDto(
    val success: Boolean,
    val data: List<SkillCategoryDto> = emptyList()
)

@Serializable
data class SkillCategoryDto(
    val id: Int,
    val name: String,
    val icon: String,
    val skills: List<SkillDto> = emptyList()
)

@Serializable
data class SkillDto(
    val id: Int,
    @SerialName("category_id") val categoryId: Int,
    val name: String
)

@Serializable
data class UserSkillResponseDto(
    val success: Boolean,
    val data: List<UserSkillDto> = emptyList()
)

@Serializable
data class UserSkillDto(
    val id: String? = null,
    @SerialName("skill_id") val skillId: Int,
    @SerialName("skill_name") val skillName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val type: String,
    @SerialName("proficiency_level") val proficiencyLevel: Int = 1,
    val note: String? = null
)

@Serializable
data class AddUserSkillDto(
    @SerialName("skill_id") val skillId: Int,
    val type: String,
    @SerialName("proficiency_level") val proficiencyLevel: Int = 1,
    val note: String? = null
)

@Serializable
data class AddUserSkillResponseDto(
    val success: Boolean,
    val data: UserSkillDto? = null,
    val message: String? = null
)

@Serializable
data class DeleteUserSkillResponseDto(
    val success: Boolean,
    val data: String? = null,
    val message: String? = null
)

class SkillRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getCategories(): SkillCategoryResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/skills/categories").body()

    suspend fun getUserSkills(accessToken: String, userId: String? = null): UserSkillResponseDto {
        val url = if (userId != null && userId.isNotBlank() && userId != "me") {
            "${Constants.BASE_URL}/api/skills/user/$userId"
        } else {
            "${Constants.BASE_URL}/api/skills/user"
        }
        return httpClient.get(url) {
            headers { append("Authorization", "Bearer $accessToken") }
        }.body()
    }

    suspend fun addUserSkill(accessToken: String, dto: AddUserSkillDto): AddUserSkillResponseDto =
        httpClient.post("${Constants.BASE_URL}/api/skills/user") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.body()

    suspend fun removeUserSkill(accessToken: String, userSkillId: String): DeleteUserSkillResponseDto =
        httpClient.delete("${Constants.BASE_URL}/api/skills/user/$userSkillId") {
            headers { append("Authorization", "Bearer $accessToken") }
        }.body()
}
