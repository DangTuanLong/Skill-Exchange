package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.stats.StatsRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StatsRepositoryTest {

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    private val sampleStatsJson = """
    {
        "success": true,
        "data": {
            "totalSessions": 12,
            "avgRating": 4.85,
            "ratingCount": 10,
            "completionRate": 0.92,
            "monthlySessions": [
                {"month": "2026-10", "label": "T10", "count": 4}
            ],
            "timeline": [
                {
                    "id": "tl-1",
                    "type": "COMPLETED_EXCHANGE",
                    "title": "Hoàn thành buổi học",
                    "description": "Đã trao đổi kỹ năng với Nguyễn B",
                    "timestamp": "2026-10-18T10:00:00Z"
                }
            ],
            "radarScores": [
                {"axisId": 1, "categoryName": "TECH", "score": 4.5, "skillCount": 3}
            ],
            "skillGapSuggestions": [
                {
                    "skillId": 5,
                    "skillName": "Figma",
                    "categoryName": "DESIGN",
                    "unlockedMatchesCount": 2,
                    "reason": "Mở khóa thêm cơ hội kết nối"
                }
            ]
        }
    }
    """.trimIndent()

    private val sampleLeaderboardJson = """
    {
        "success": true,
        "data": [
            {
                "rank": 1,
                "userId": "user-top-1",
                "fullName": "Top User",
                "avatarUrl": null,
                "city": "Hà Nội",
                "score": 4.95,
                "badge": "Xuất sắc",
                "totalExchanges": 20,
                "ratingCount": 18
            }
        ]
    }
    """.trimIndent()

    @Test
    fun getUserStats_Success_ReturnsUserStats() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleStatsJson)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = StatsRepositoryImpl(StatsRemoteDataSource(client), tokenManager)

        val result = repo.getUserStats()

        assertTrue(result.isSuccess)
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(12, stats?.totalSessions)
        assertEquals(4.85, stats?.avgRating ?: 0.0, 0.01)
        assertEquals(0.92, stats?.completionRate ?: 0.0, 0.01)
        assertEquals(1, stats?.monthlySessions?.size)
        assertEquals(1, stats?.timeline?.size)
        assertEquals(1, stats?.radarScores?.size)
        assertEquals(1, stats?.skillGapSuggestions?.size)
    }

    @Test
    fun getUserStats_NotLoggedIn_ReturnsFailure() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleStatsJson)
        val tokenManager = TokenManager(null) // no token
        val repo = StatsRepositoryImpl(StatsRemoteDataSource(client), tokenManager)

        val result = repo.getUserStats()

        assertTrue(result.isFailure)
    }

    @Test
    fun getLeaderboard_Success_ReturnsLeaderboardList() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleLeaderboardJson)
        val tokenManager = TokenManager(null)
        val repo = StatsRepositoryImpl(StatsRemoteDataSource(client), tokenManager)

        val result = repo.getLeaderboard()

        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertNotNull(list)
        assertEquals(1, list?.size)
        assertEquals(1, list?.first()?.rank)
        assertEquals("Top User", list?.first()?.fullName)
        assertEquals("Xuất sắc", list?.first()?.badge)
    }
}
