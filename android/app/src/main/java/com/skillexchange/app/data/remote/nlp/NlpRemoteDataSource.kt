package com.skillexchange.app.data.remote.nlp

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class NlpRemoteDataSource(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extractSkills(accessToken: String, text: String): ExtractSkillsResponse {
        val response = httpClient.post("${Constants.BASE_URL}/api/nlp/extract-skills") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = text))
        }
        if (!response.status.isSuccess()) {
            val errorText = response.bodyAsText()
            val errorMsg = try {
                val errDto = json.decodeFromString<NlpApiErrorDto>(errorText)
                errDto.message
            } catch (_: Exception) {
                "Lỗi máy chủ (${response.status.value})"
            }
            throw RuntimeException(errorMsg)
        }
        return response.body()
    }

    suspend fun extractSkillsFromDocument(
        accessToken: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = inferMimeType(fileName)
    ): ExtractSkillsResponse {
        val response = httpClient.submitFormWithBinaryData(
            url = "${Constants.BASE_URL}/api/nlp/extract-skills-document",
            formData = formData {
                append("file", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (!response.status.isSuccess()) {
            val errorText = response.bodyAsText()
            val errorMsg = try {
                val errDto = json.decodeFromString<NlpApiErrorDto>(errorText)
                errDto.message
            } catch (_: Exception) {
                "Lỗi máy chủ (${response.status.value})"
            }
            throw RuntimeException(errorMsg)
        }
        return response.body()
    }

    companion object {
        fun inferMimeType(fileName: String): String {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".md") -> "text/markdown"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".webp") -> "image/webp"
                else -> "text/plain"
            }
        }
    }
}
