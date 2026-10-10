package com.skillexchange.app.data.remote.chat

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
data class ChatAttachmentDataDto(
    val url: String,
    val type: String,
    val fileName: String,
    val fileSize: Long
)

@Serializable
data class ChatAttachmentResponseDto(
    val success: Boolean,
    val data: ChatAttachmentDataDto? = null,
    val message: String? = null
)

class ChatRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun uploadAttachment(
        accessToken: String,
        chatId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): ChatAttachmentResponseDto =
        httpClient.submitFormWithBinaryData(
            url = "${Constants.BASE_URL}/api/chat/attachment",
            formData = formData {
                append("chatId", chatId)
                append("file", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()
}
