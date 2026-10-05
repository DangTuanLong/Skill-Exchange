package com.skillexchange.app.data.remote.profile

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvailabilityWindowDto(
    val day: String,
    val from: String,
    val to: String
)

@Serializable
data class ProfileResponseDto(
    val success: Boolean,
    val data: ProfileDataDto? = null,
    val message: String? = null
)

@Serializable
data class ProfileDataDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("full_name") val fullName: String = "",
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val availability: List<AvailabilityWindowDto> = emptyList()
)

@Serializable
data class UpdateProfileDto(
    @SerialName("full_name") val fullName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val availability: List<AvailabilityWindowDto>? = null
)


@Serializable
data class AvatarUploadDataDto(
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class AvatarUploadResponseDto(
    val success: Boolean,
    val data: AvatarUploadDataDto? = null,
    val message: String? = null
)

class ProfileRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getMyProfile(accessToken: String): ProfileResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/profile/me") {
            headers { append("Authorization", "Bearer $accessToken") }
        }.body()

    suspend fun getUserProfile(userId: String): ProfileResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/profile/$userId").body()

    suspend fun updateProfile(accessToken: String, dto: UpdateProfileDto): ProfileResponseDto =
        httpClient.put("${Constants.BASE_URL}/api/profile") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.body()

    suspend fun uploadAvatar(
        accessToken: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): AvatarUploadResponseDto =
        httpClient.submitFormWithBinaryData(
            url = "${Constants.BASE_URL}/api/profile/avatar",
            formData = formData {
                append("avatar", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()
}
