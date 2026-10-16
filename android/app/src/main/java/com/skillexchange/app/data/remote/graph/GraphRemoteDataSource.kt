package com.skillexchange.app.data.remote.graph

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter

class GraphRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getSuggestedPaths(
        accessToken: String,
        depth: Int = 3,
        limit: Int = 5
    ): SuggestPathResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/graph/suggest-path") {
            headers { append("Authorization", "Bearer $accessToken") }
            parameter("depth", depth)
            parameter("limit", limit)
        }.body()
}
