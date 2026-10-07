package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.rating.CreateRatingRequest
import com.skillexchange.api.models.rating.RatingDto
import com.skillexchange.api.models.rating.ReputationDto
import com.skillexchange.api.models.rating.UserExchangeRatingStatusDto
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.rating.RatingConflictException
import com.skillexchange.api.services.rating.RatingForbiddenException
import com.skillexchange.api.services.rating.RatingNotFoundException
import com.skillexchange.api.services.rating.RatingService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatingRoutesTest {

    private val mockRatingService = mockk<RatingService>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        clearMocks(mockRatingService)
    }

    private fun createTestJwt(userId: String = "user-caller-111"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun ApplicationTestBuilder.setupRatingRoutesModule() {
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
                ratingRoutes(mockRatingService)
            }
        }
    }

    private fun sampleRatingDto() = RatingDto(
        id = "rating-1",
        exchangeId = "exch-1",
        reviewerId = "user-caller-111",
        reviewerName = "Reviewer A",
        reviewerAvatarUrl = null,
        revieweeId = "user-target-222",
        score = 5,
        comment = "Trao đổi rất tốt!",
        createdAt = "2026-10-07T12:00:00"
    )

    // ─────────────────────────────────────────────────────────────
    // 1. POST /api/ratings
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `post rating - Returns 201 Created when valid`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-caller-111"
        val expectedDto = sampleRatingDto()

        every { mockRatingService.createRating(callerId, any()) } returns expectedDto

        val response = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "exch-1", "score": 5, "comment": "Tuyệt vời!"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("rating-1"))
        verify(exactly = 1) { mockRatingService.createRating(callerId, any()) }
    }

    @Test
    fun `post rating - Returns 401 Unauthorized without token`() = testApplication {
        setupRatingRoutesModule()

        val response = client.post("/api/ratings") {
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "exch-1", "score": 5}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNAUTHORIZED, error.code)
    }

    @Test
    fun `post rating - Returns 403 Forbidden when caller is not participant`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-stranger-999"

        every { mockRatingService.createRating(callerId, any()) } throws RatingForbiddenException("Bạn không phải thành viên của phiên trao đổi này")

        val response = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "exch-1", "score": 4}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertTrue(error.code == ErrorCodes.FORBIDDEN || error.code == ErrorCodes.NOT_PARTICIPANT)
    }

    @Test
    fun `post rating - Returns 404 Not Found when exchange does not exist`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-caller-111"

        every { mockRatingService.createRating(callerId, any()) } throws RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")

        val response = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "non-existent", "score": 4}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.NOT_FOUND, error.code)
    }

    @Test
    fun `post rating - Returns 422 UnprocessableEntity when exchange is not COMPLETED or score invalid`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-caller-111"

        every { mockRatingService.createRating(callerId, any()) } throws ValidationException("Chỉ có thể đánh giá phiên trao đổi đã hoàn thành (COMPLETED)")

        val response = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "exch-1", "score": 5}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, error.code)
    }

    @Test
    fun `post rating - Returns 409 Conflict when already rated`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-caller-111"

        every { mockRatingService.createRating(callerId, any()) } throws RatingConflictException("Bạn đã đánh giá phiên trao đổi này rồi")

        val response = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id": "exch-1", "score": 5}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.ALREADY_RATED, error.code)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET /api/ratings/user/{id}
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `get user ratings - Returns 200 OK with ratings list`() = testApplication {
        setupRatingRoutesModule()
        val targetUserId = "user-target-222"

        every { mockRatingService.getUserRatings(targetUserId) } returns listOf(sampleRatingDto())

        val response = client.get("/api/ratings/user/$targetUserId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("rating-1"))
        verify(exactly = 1) { mockRatingService.getUserRatings(targetUserId) }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET /api/reputation/{id}
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `get reputation - Returns 200 OK with calculated reputation`() = testApplication {
        setupRatingRoutesModule()
        val targetUserId = "user-target-222"
        val expectedReputation = ReputationDto(
            score = 4.8,
            badge = "Xuất sắc",
            totalExchanges = 16,
            completionRate = 1.0,
            ratingCount = 5,
            avgRating = 4.8
        )

        every { mockRatingService.getUserReputation(targetUserId) } returns expectedReputation

        val response = client.get("/api/reputation/$targetUserId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Xuất sắc"))
        assertTrue(response.bodyAsText().contains("4.8"))
    }

    // ─────────────────────────────────────────────────────────────
    // 4. GET /api/ratings/exchange/{id}/mine
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `get mine rating status - Returns 200 OK with has_rated status`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-caller-111"
        val exchangeId = "exch-1"

        every { mockRatingService.getMyExchangeRating(callerId, exchangeId) } returns UserExchangeRatingStatusDto(
            hasRated = true,
            rating = sampleRatingDto()
        )

        val response = client.get("/api/ratings/exchange/$exchangeId/mine") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"has_rated\":true"))
    }

    @Test
    fun `get mine rating status - Returns 403 when not participant`() = testApplication {
        setupRatingRoutesModule()
        val callerId = "user-stranger-999"
        val exchangeId = "exch-1"

        every { mockRatingService.getMyExchangeRating(callerId, exchangeId) } throws RatingForbiddenException("Bạn không phải thành viên của phiên trao đổi này")

        val response = client.get("/api/ratings/exchange/$exchangeId/mine") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertTrue(error.code == ErrorCodes.FORBIDDEN || error.code == ErrorCodes.NOT_PARTICIPANT)
    }
}
