package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.exchange.CancelExchangeRequest
import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.exchange.ExchangeConflictException
import com.skillexchange.api.services.exchange.ExchangeForbiddenException
import com.skillexchange.api.services.exchange.ExchangeService
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
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRoutesTest {

    private val mockExchangeService = mockk<ExchangeService>(relaxed = true)

    @BeforeTest
    fun setUp() {
        clearMocks(mockExchangeService)
    }

    private fun createTestJwt(userId: String = "user-caller-111"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupExchangeRoutesModule() {
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
                exchangeRoutes(mockExchangeService)
            }
        }
    }

    private fun sampleDto(
        id: String = "exch-123",
        senderId: String = "user-caller-111",
        receiverId: String = "user-receiver-222",
        status: String = "PENDING"
    ) = ExchangeRequestDto(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        senderName = "Nguyen Van A",
        senderAvatarUrl = null,
        receiverName = "Tran Thi B",
        receiverAvatarUrl = null,
        skillOfferedId = 1,
        skillOfferedName = "Kotlin",
        skillWantedId = 2,
        skillWantedName = "English",
        status = status,
        durationMinutes = 60,
        meetingMode = "ONLINE",
        message = "Trao đổi kỹ năng",
        cancellationReason = null,
        scheduledAt = "2026-10-15T19:00:00",
        senderCompletedAt = null,
        receiverCompletedAt = null,
        createdAt = "2026-10-05T12:00:00",
        updatedAt = "2026-10-05T12:00:00"
    )

    // ─────────────────────────────────────────────────────────────
    // 1. POST /api/exchange-requests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `POST create request - returns 401 without JWT`() = testApplication {
        setupExchangeRoutesModule()
        val clientWithJson = createClient { install(ContentNegotiation) { json() } }

        val response = clientWithJson.post("/api/exchange-requests") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateExchangeRequest(
                    receiverId = "user-222",
                    skillOfferedId = 1,
                    skillWantedId = 2,
                    scheduledAt = "2026-10-15T19:00:00"
                )
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST create request - returns 422 when validation fails`() = testApplication {
        val callerId = "user-caller-111"
        every {
            mockExchangeService.createExchangeRequest(callerId, any())
        } throws ValidationException("Thời gian hẹn phải ở tương lai")

        setupExchangeRoutesModule()
        val clientWithJson = createClient { install(ContentNegotiation) { json() } }

        val response = clientWithJson.post("/api/exchange-requests") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateExchangeRequest(
                    receiverId = "user-222",
                    skillOfferedId = 1,
                    skillWantedId = 2,
                    scheduledAt = "2020-01-01T10:00:00"
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Thời gian hẹn phải ở tương lai"))
    }

    @Test
    fun `POST create request - returns 201 Created on success`() = testApplication {
        val callerId = "user-caller-111"
        val expected = sampleDto()
        every { mockExchangeService.createExchangeRequest(callerId, any()) } returns expected

        setupExchangeRoutesModule()
        val clientWithJson = createClient { install(ContentNegotiation) { json() } }

        val response = clientWithJson.post("/api/exchange-requests") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateExchangeRequest(
                    receiverId = "user-receiver-222",
                    skillOfferedId = 1,
                    skillWantedId = 2,
                    scheduledAt = "2026-10-15T19:00:00"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("exch-123"))
        assertTrue(response.bodyAsText().contains("PENDING"))
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET incoming and outgoing
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GET incoming - returns 200 with list`() = testApplication {
        val callerId = "user-caller-111"
        every { mockExchangeService.getIncomingRequests(callerId) } returns listOf(sampleDto())

        setupExchangeRoutesModule()

        val response = client.get("/api/exchange-requests/incoming") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("exch-123"))
    }

    @Test
    fun `GET outgoing - returns 200 with list`() = testApplication {
        val callerId = "user-caller-111"
        every { mockExchangeService.getOutgoingRequests(callerId) } returns listOf(sampleDto())

        setupExchangeRoutesModule()

        val response = client.get("/api/exchange-requests/outgoing") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("exch-123"))
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET by ID
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `GET by id - returns 404 when not found`() = testApplication {
        val callerId = "user-caller-111"
        every { mockExchangeService.getExchangeRequest(callerId, "non-existent") } returns null

        setupExchangeRoutesModule()

        val response = client.get("/api/exchange-requests/non-existent") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET by id - returns 403 when user is not a participant`() = testApplication {
        val callerId = "user-caller-111"
        every {
            mockExchangeService.getExchangeRequest(callerId, "exch-123")
        } throws ExchangeForbiddenException("Bạn không phải thành viên tham gia yêu cầu này")

        setupExchangeRoutesModule()

        val response = client.get("/api/exchange-requests/exch-123") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET by id - returns 200 with dto when authorized`() = testApplication {
        val callerId = "user-caller-111"
        every { mockExchangeService.getExchangeRequest(callerId, "exch-123") } returns sampleDto()

        setupExchangeRoutesModule()

        val response = client.get("/api/exchange-requests/exch-123") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("exch-123"))
    }

    // ─────────────────────────────────────────────────────────────
    // 4. PUT accept & reject
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PUT accept - returns 403 when sender tries to accept`() = testApplication {
        val senderId = "user-sender-111"
        every {
            mockExchangeService.acceptRequest(senderId, "exch-123")
        } throws ExchangeForbiddenException("Chỉ người nhận mới có quyền chấp nhận yêu cầu")

        setupExchangeRoutesModule()

        val response = client.put("/api/exchange-requests/exch-123/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(senderId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `PUT accept - returns 409 when invalid transition`() = testApplication {
        val receiverId = "user-receiver-222"
        every {
            mockExchangeService.acceptRequest(receiverId, "exch-123")
        } throws ExchangeConflictException("Không thể chấp nhận yêu cầu ở trạng thái CANCELLED")

        setupExchangeRoutesModule()

        val response = client.put("/api/exchange-requests/exch-123/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(receiverId)}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT accept - returns 200 when receiver accepts`() = testApplication {
        val receiverId = "user-receiver-222"
        every { mockExchangeService.acceptRequest(receiverId, "exch-123") } returns sampleDto(status = "ACCEPTED")

        setupExchangeRoutesModule()

        val response = client.put("/api/exchange-requests/exch-123/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(receiverId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ACCEPTED"))
    }

    @Test
    fun `PUT reject - returns 200 when receiver rejects`() = testApplication {
        val receiverId = "user-receiver-222"
        every { mockExchangeService.rejectRequest(receiverId, "exch-123") } returns sampleDto(status = "REJECTED")

        setupExchangeRoutesModule()

        val response = client.put("/api/exchange-requests/exch-123/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(receiverId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("REJECTED"))
    }

    // ─────────────────────────────────────────────────────────────
    // 5. PUT cancel & complete
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `PUT cancel - returns 200 when cancelled`() = testApplication {
        val callerId = "user-caller-111"
        every {
            mockExchangeService.cancelRequest(callerId, "exch-123", "Bận việc")
        } returns sampleDto(status = "CANCELLED")

        setupExchangeRoutesModule()
        val clientWithJson = createClient { install(ContentNegotiation) { json() } }

        val response = clientWithJson.put("/api/exchange-requests/exch-123/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            contentType(ContentType.Application.Json)
            setBody(CancelExchangeRequest(reason = "Bận việc"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CANCELLED"))
    }

    @Test
    fun `PUT complete - returns 200 when confirmed`() = testApplication {
        val callerId = "user-caller-111"
        every {
            mockExchangeService.completeRequest(callerId, "exch-123")
        } returns sampleDto(status = "COMPLETED")

        setupExchangeRoutesModule()

        val response = client.put("/api/exchange-requests/exch-123/complete") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("COMPLETED"))
    }
}
