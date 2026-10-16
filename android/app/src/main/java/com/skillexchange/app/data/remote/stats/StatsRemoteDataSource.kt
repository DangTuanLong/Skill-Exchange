package com.skillexchange.app.data.remote.stats

import com.skillexchange.app.core.common.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers

class StatsRemoteDataSource(private val httpClient: HttpClient) {

    suspend fun getUserStats(accessToken: String): UserStatsResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/stats/me") {
            headers { append("Authorization", "Bearer $accessToken") }
        }.body()

    suspend fun getLeaderboard(accessToken: String? = null): LeaderboardResponseDto =
        httpClient.get("${Constants.BASE_URL}/api/leaderboard") {
            if (!accessToken.isNullOrBlank()) {
                headers { append("Authorization", "Bearer $accessToken") }
            }
        }.body()
}
