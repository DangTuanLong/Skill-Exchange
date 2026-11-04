package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import com.skillexchange.api.models.exchange.RescheduleProposalDto
import com.skillexchange.api.models.exchange.RescheduleStatus
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.exchange.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RescheduleRoutesTest {

    private val mockRescheduleService = mockk<RescheduleService>(relaxed = true)
    private val mockRateLimiter = mockk<RescheduleRateLimiter>(relaxed = true)
    private val jsonSerializer = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        clearMocks(mockRescheduleService, mockRateLimiter)
        every { mockRateLimiter.tryAcquire(any()) } returns true
    }

    private fun createTestJwt(userId: String = "user-alice-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun sampleProposalDto(
        id: String = UUID.randomUUID().toString(),
        exchangeId: String = UUID.randomUUID().toString(),
        proposerId: String = "user-alice-123",
        status: String = RescheduleStatus.PENDING.name,
        proposalNumber: Int = 1
    ) = RescheduleProposalDto(
        id = id,
        exchangeId = exchangeId,
        proposerId = proposerId,
        newScheduledAt = LocalDateTime.now().plusDays(2).toString(),
        reason = "Bận việc đột xuất",
        status = status,
        proposalNumber = proposalNumber,
        respondedAt = null,
        createdAt = LocalDateTime.now().toString(),
        updatedAt = LocalDateTime.now().toString()
    )

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupRescheduleModule() {
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
                        if (credential.payload.audience.contains("authenticated")) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                rescheduleRoutes(mockRescheduleService, mockRateLimiter)
            }
        }
    }

    // --- Authentication Tests ---

    @Test
    fun `POST reschedule - no JWT returns 401 Unauthorized`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/exchange-requests/${UUID.randomUUID()}/reschedule") {
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z","reason":"Bận"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET current - no JWT returns 401 Unauthorized`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/exchange-requests/${UUID.randomUUID()}/reschedule/current")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT accept - no JWT returns 401 Unauthorized`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.put("/api/reschedule-proposals/${UUID.randomUUID()}/accept")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT reject - no JWT returns 401 Unauthorized`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.put("/api/reschedule-proposals/${UUID.randomUUID()}/reject")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT cancel - no JWT returns 401 Unauthorized`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.put("/api/reschedule-proposals/${UUID.randomUUID()}/cancel")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Rate Limit Tests ---

    @Test
    fun `POST reschedule - returns 429 when rate limit exceeded`() = testApplication {
        setupRescheduleModule()
        every { mockRateLimiter.tryAcquire("user-alice-123") } returns false

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/${UUID.randomUUID()}/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z","reason":"Bận"}""")
        }

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, err.code)
    }

    // --- POST /reschedule Tests ---

    @Test
    fun `POST reschedule - success returns 201 Created`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        val expectedDto = sampleProposalDto(exchangeId = exchangeId)

        every {
            mockRescheduleService.createProposal(
                callerUserId = "user-alice-123",
                exchangeId = exchangeId,
                req = any()
            )
        } returns expectedDto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$exchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z","reason":"Bận việc đột xuất"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains(expectedDto.id))
        assertTrue(bodyText.contains("Bận việc đột xuất"))
    }

    @Test
    fun `POST reschedule - returns 400 Bad Request on malformed JSON`() = testApplication {
        setupRescheduleModule()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/exchange-requests/${UUID.randomUUID()}/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            contentType(ContentType.Application.Json)
            setBody("""{"bad_json""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST reschedule - returns 403 NOT_PARTICIPANT when caller is not member`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.createProposal("user-alice-123", exchangeId, any())
        } throws RescheduleForbiddenException("Bạn không phải thành viên", ErrorCodes.NOT_PARTICIPANT)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$exchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertTrue(err.code == ErrorCodes.FORBIDDEN || err.code == ErrorCodes.NOT_PARTICIPANT)
    }

    @Test
    fun `POST reschedule - returns 409 PENDING_PROPOSAL_EXISTS when another proposal is active`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.createProposal("user-alice-123", exchangeId, any())
        } throws RescheduleConflictException("Đang có đề xuất PENDING", ErrorCodes.PENDING_PROPOSAL_EXISTS)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$exchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PENDING_PROPOSAL_EXISTS, err.code)
    }

    @Test
    fun `POST reschedule - returns 422 RESCHEDULE_LIMIT_EXCEEDED when 3 proposals used`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.createProposal("user-alice-123", exchangeId, any())
        } throws RescheduleValidationException("Hết 3 lần đổi lịch", ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$exchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-10T10:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED, err.code)
    }

    @Test
    fun `POST reschedule - returns 422 INVALID_RESCHEDULE_TIME when time is less than 15m ahead`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.createProposal("user-alice-123", exchangeId, any())
        } throws RescheduleValidationException("Thời gian phải sau 15 phút", ErrorCodes.INVALID_RESCHEDULE_TIME)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$exchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-04T10:05:00Z"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, err.code)
    }

    // --- GET /current Tests ---

    @Test
    fun `GET current - returns 200 OK with proposal when found`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        val dto = sampleProposalDto(exchangeId = exchangeId)
        every { mockRescheduleService.getCurrentProposal("user-alice-123", exchangeId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/exchange-requests/$exchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains(dto.id))
    }

    @Test
    fun `GET current - returns 200 OK with null data when no proposal exists`() = testApplication {
        setupRescheduleModule()
        val exchangeId = UUID.randomUUID().toString()
        every { mockRescheduleService.getCurrentProposal("user-alice-123", exchangeId) } returns null

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/exchange-requests/$exchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"data\":null"))
    }

    // --- PUT /accept Tests ---

    @Test
    fun `PUT accept - partner accepts returns 200 OK`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        val dto = sampleProposalDto(id = proposalId, status = RescheduleStatus.ACCEPTED.name)
        every { mockRescheduleService.acceptProposal("user-bob-456", proposalId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-bob-456")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ACCEPTED"))
    }

    @Test
    fun `PUT accept - proposer cannot accept own proposal returns 403 Forbidden`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.acceptProposal("user-alice-123", proposalId)
        } throws RescheduleForbiddenException("Không thể tự chấp nhận đề xuất của chính mình")

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `PUT accept - returns 409 PROPOSAL_NOT_PENDING if proposal not pending`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.acceptProposal("user-bob-456", proposalId)
        } throws RescheduleConflictException("Đề xuất không ở trạng thái PENDING", ErrorCodes.PROPOSAL_NOT_PENDING)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-bob-456")}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val err = jsonSerializer.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PROPOSAL_NOT_PENDING, err.code)
    }

    // --- PUT /reject Tests ---

    @Test
    fun `PUT reject - partner rejects returns 200 OK`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        val dto = sampleProposalDto(id = proposalId, status = RescheduleStatus.REJECTED.name)
        every { mockRescheduleService.rejectProposal("user-bob-456", proposalId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-bob-456")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("REJECTED"))
    }

    @Test
    fun `PUT reject - proposer cannot reject own proposal returns 403 Forbidden`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.rejectProposal("user-alice-123", proposalId)
        } throws RescheduleForbiddenException("Không thể từ chối đề xuất do chính mình tạo")

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // --- PUT /cancel Tests ---

    @Test
    fun `PUT cancel - proposer cancels returns 200 OK`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        val dto = sampleProposalDto(id = proposalId, status = RescheduleStatus.CANCELLED.name)
        every { mockRescheduleService.cancelProposal("user-alice-123", proposalId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-alice-123")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CANCELLED"))
    }

    @Test
    fun `PUT cancel - non-proposer partner cannot cancel returns 403 Forbidden`() = testApplication {
        setupRescheduleModule()
        val proposalId = UUID.randomUUID().toString()
        every {
            mockRescheduleService.cancelProposal("user-bob-456", proposalId)
        } throws RescheduleForbiddenException("Chỉ người tạo mới có quyền hủy đề xuất")

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$proposalId/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-bob-456")}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}

