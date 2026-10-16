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
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class NlpRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun extractSkills(accessToken: String, text: String): ExtractSkillsResponse =
        httpClient.post("${Constants.BASE_URL}/api/nlp/extract-skills") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = text))
        }.body()

    suspend fun extractSkillsFromDocument(
        accessToken: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "text/plain"
    ): ExtractSkillsResponse =
        httpClient.submitFormWithBinaryData(
            url = "${Constants.BASE_URL}/api/nlp/extract-skills-document",
            formData = formData {
                append("file", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()
}
