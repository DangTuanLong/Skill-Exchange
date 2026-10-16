package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.stats.*
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.stats.StatsService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsRoutesTest {

    private val mockStatsService = mockk<StatsService>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        clearMocks(mockStatsService)
    }

    private fun createTestJwt(userId: String = "user-test-caller"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupStatsRoutesModule() {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "SkillExchange API"
                    verifier(
                        JWT.require(Algorithm.HMAC256("test-secret"))
                            .withAudience("authenticated")
                            .build()
                    )
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                statsRoutes(mockStatsService)
            }
        }
    }

    @Test
    fun `GET stats me without JWT returns 401 Unauthorized`() = testApplication {
        setupStatsRoutesModule()

        val response = client.get("/api/stats/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET stats me with valid JWT returns UserStatsResponse`() = testApplication {
        setupStatsRoutesModule()

        val expectedStats = UserStatsDto(
            totalSessions = 5,
            avgRating = 4.8,
            ratingCount = 4,
            completionRate = 1.0,
            monthlySessions = listOf(
                MonthlySessionDto("2026-05", "05/2026", 2),
                MonthlySessionDto("2026-04", "04/2026", 3)
            ),
            timeline = listOf(
                TimelineItemDto("e1", "EXCHANGE", "Hoàn thành buổi trao đổi", "Trao đổi với Lan", "2026-05-10T10:00:00")
            ),
            radarScores = listOf(
                RadarAxisDto(1, "Lập trình", 5.0, 2),
                RadarAxisDto(2, "Ngoại ngữ", 3.0, 1)
            ),
            skillGapSuggestions = listOf(
                SkillGapSuggestionDto(3, "Piano", "Âm nhạc", 2, "Có 2 thành viên cần kỹ năng của bạn")
            )
        )

        every { mockStatsService.getUserStats("user-test-caller", any()) } returns expectedStats

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/stats/me") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-caller")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<UserStatsResponse>(body)
        assertTrue(parsed.success)
        assertEquals(5, parsed.data.totalSessions)
        assertEquals(4.8, parsed.data.avgRating)
        assertEquals(1, parsed.data.timeline.size)
        assertEquals(1, parsed.data.skillGapSuggestions.size)
    }

    @Test
    fun `GET leaderboard returns top 10 users list`() = testApplication {
        setupStatsRoutesModule()

        val expectedLeaderboard = (1..10).map { i ->
            LeaderboardItemDto(
                rank = i,
                userId = "u$i",
                fullName = "Top User $i",
                avatarUrl = null,
                city = "Hà Nội",
                score = 5.0 - (i * 0.1),
                badge = if (i <= 3) "Xuất sắc" else "Đáng tin cậy",
                totalExchanges = 20 - i,
                ratingCount = 15 - i
            )
        }

        every { mockStatsService.getLeaderboard() } returns expectedLeaderboard

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/leaderboard")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<LeaderboardResponse>(body)
        assertTrue(parsed.success)
        assertEquals(10, parsed.data.size)
        assertEquals(1, parsed.data[0].rank)
        assertEquals("Top User 1", parsed.data[0].fullName)
    }
}
