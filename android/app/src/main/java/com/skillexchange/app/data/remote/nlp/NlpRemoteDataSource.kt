package com.skillexchange.app.data.remote.nlp

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class NlpRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun extractSkills(accessToken: String, text: String): ExtractSkillsResponse =
        httpClient.post("${Constants.BASE_URL}/api/nlp/extract-skills") {
            headers { append("Authorization", "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = text))
        }.body()
}
