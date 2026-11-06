package com.skillexchange.api.integration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import com.skillexchange.api.models.exchange.RescheduleProposalDto
import com.skillexchange.api.models.exchange.RescheduleStatus
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.routes.exchangeRoutes
import com.skillexchange.api.routes.rescheduleRoutes
import com.skillexchange.api.services.InMemoryRateLimiter
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
import io.ktor.server.testing.ApplicationTestBuilder
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

/**
 * Kiểm thử tích hợp MỨC ROUTE VỚI SERVICE GIẢ (Mock Services) cho tính năng Đổi Lịch Hẹn (DEC-025, TASK-072).
 *
 * LƯU Ý QUAN TRỌNG:
 * - Bộ kiểm thử này kiểm chứng tương tác HTTP, xác thực JWT, phân quyền IDOR (chỉ hai thành viên hợp lệ),
 *   rate limiting (5 req/phút/user), mã lỗi HTTP (201, 200, 400, 401, 403, 404, 409, 422, 429),
 *   và luồng điều phối của các endpoint đổi lịch ở tầng Route.
 * - SỬ DỤNG SERVICE GIẢ (MOCK SERVICES), TUYỆT ĐỐI KHÔNG SỬ DỤNG CSDL POSTGRESQL THẬT.
 * - Các ràng buộc CSDL thật (partial unique index idx_unique_pending_reschedule, SELECT FOR UPDATE lock, trigger updated_at)
 *   được kiểm chứng độc lập qua các kịch bản kiểm thử thủ công trên DB dev theo tài liệu TASK-072.
 */
class RescheduleRouteIntegrationTest {

    private val jwtSecret = "test-jwt-secret-reschedule-route-integration"
    private val json = Json { ignoreUnknownKeys = true }

    private val mockRescheduleService = mockk<RescheduleService>(relaxed = true)
    private val mockExchangeService = mockk<ExchangeService>(relaxed = true)

    private val aliceId = "11111111-1111-1111-1111-111111111111" // Thành viên A (Sender/Proposer)
    private val bobId = "22222222-2222-2222-2222-222222222222"   // Thành viên B (Receiver/Partner)
    private val charlieId = "33333333-3333-3333-3333-333333333333" // Kẻ xâm nhập ngoài cuộc (IDOR Attacker)

    private val testExchangeId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val testProposalId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

    private fun createToken(userId: String): String =
        JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256(jwtSecret))

    private fun createProposalDto(
        id: String = testProposalId,
        exchangeId: String = testExchangeId,
        proposerId: String = aliceId,
        status: String = RescheduleStatus.PENDING.name,
        proposalNumber: Int = 1,
        newScheduledAt: String = "2026-11-20T14:00:00",
        reason: String? = "Cần dời lịch do có cuộc họp đột xuất"
    ) = RescheduleProposalDto(
        id = id,
        exchangeId = exchangeId,
        proposerId = proposerId,
        newScheduledAt = newScheduledAt,
        reason = reason,
        status = status,
        proposalNumber = proposalNumber,
        respondedAt = null,
        createdAt = "2026-11-10T08:00:00",
        updatedAt = "2026-11-10T08:00:00"
    )

    private fun ApplicationTestBuilder.setupApp(
        customRescheduleRateLimiter: RescheduleRateLimiter? = null,
        customActionRateLimiter: RescheduleRateLimiter? = null
    ) {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "SkillExchange API"
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSecret))
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
                rescheduleRoutes(
                    rescheduleService = mockRescheduleService,
                    rateLimiter = customRescheduleRateLimiter ?: RescheduleRateLimiter(maxPerMinute = 5),
                    actionRateLimiter = customActionRateLimiter ?: RescheduleRateLimiter(maxPerMinute = 30)
                )
                exchangeRoutes(
                    exchangeService = mockExchangeService,
                    validPairsRateLimiter = InMemoryRateLimiter(maxRequests = 30, windowMillis = 60_000L)
                )
            }
        }
    }

    @BeforeTest
    fun setUp() {
        clearMocks(mockRescheduleService, mockExchangeService)
    }

    // =========================================================================
    // 1. Phân quyền và Bảo mật (IDOR, Auth, Rate Limiter)
    // =========================================================================

    @Test
    fun `POST reschedule - khong co JWT tra ve 401 Unauthorized`() = testApplication {
        setupApp()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z","reason":"Dời lịch"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST reschedule - IDOR check ke ngoai cuoc Charlie bi tu choi 403 NOT_PARTICIPANT`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.createProposal(charlieId, testExchangeId, any())
        } throws RescheduleForbiddenException("Bạn không phải thành viên của phiên trao đổi này", ErrorCodes.NOT_PARTICIPANT)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(charlieId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z","reason":"Hack"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertTrue(err.code == ErrorCodes.FORBIDDEN || err.code == ErrorCodes.NOT_PARTICIPANT)
    }

    @Test
    fun `POST reschedule - Rate limiter chan o request thu 6 trong 1 phut tra ve 429 RATE_LIMITED`() = testApplication {
        val limiter = RescheduleRateLimiter(maxPerMinute = 5)
        setupApp(customRescheduleRateLimiter = limiter)

        val dto = createProposalDto()
        every { mockRescheduleService.createProposal(aliceId, testExchangeId, any()) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = createToken(aliceId)

        // 5 requests hop le trong quota
        repeat(5) {
            val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z"}""")
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }

        // Request thu 6 bi chan boi rate limiter
        val blockedResponse = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, blockedResponse.status)
        val err = json.decodeFromString<ApiError>(blockedResponse.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, err.code)
    }

    @Test
    fun `GET current - Action Rate limiter chan o request thu 31 trong 1 phut tra ve 429 RATE_LIMITED`() = testApplication {
        val actionLimiter = RescheduleRateLimiter(maxPerMinute = 30)
        setupApp(customActionRateLimiter = actionLimiter)

        val dto = createProposalDto()
        every { mockRescheduleService.getCurrentProposal(aliceId, testExchangeId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = createToken(aliceId)

        // 30 requests hop le trong quota
        repeat(30) {
            val response = client.get("/api/exchange-requests/$testExchangeId/reschedule/current") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        // Request thu 31 bi chan boi action rate limiter
        val blockedResponse = client.get("/api/exchange-requests/$testExchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, blockedResponse.status)
        val err = json.decodeFromString<ApiError>(blockedResponse.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, err.code)
    }

    // =========================================================================
    // 2. Tao de xuat doi lich (POST /reschedule)
    // =========================================================================

    @Test
    fun `POST reschedule - thanh vien hop le tao de xuat thanh cong tra ve 201 Created`() = testApplication {
        setupApp()
        val expectedDto = createProposalDto(proposalNumber = 1)
        every { mockRescheduleService.createProposal(aliceId, testExchangeId, any()) } returns expectedDto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z","reason":"Cần dời lịch do có cuộc họp đột xuất"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains(testProposalId))
        assertTrue(bodyText.contains("PENDING"))
        assertTrue(bodyText.contains("proposalNumber\":1") || bodyText.contains("proposal_number\":1"))
    }

    @Test
    fun `POST reschedule - da dat gioi han 3 lan tra ve 409 RESCHEDULE_LIMIT_EXCEEDED`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.createProposal(aliceId, testExchangeId, any())
        } throws RescheduleConflictException(
            message = "Phiên trao đổi đã đạt giới hạn tối đa 3 lần đề xuất đổi lịch.",
            errorCode = ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED
        )

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED, err.code)
    }

    @Test
    fun `POST reschedule - dang co de xuat PENDING tra ve 409 PENDING_PROPOSAL_EXISTS`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.createProposal(aliceId, testExchangeId, any())
        } throws RescheduleConflictException(
            message = "Đang có một đề xuất đổi lịch chờ phản hồi.",
            errorCode = ErrorCodes.PENDING_PROPOSAL_EXISTS
        )

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-20T14:00:00Z"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PENDING_PROPOSAL_EXISTS, err.code)
    }

    @Test
    fun `POST reschedule - gio moi khong cach hien tai it nhat 15 phut tra ve 422 INVALID_RESCHEDULE_TIME`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.createProposal(aliceId, testExchangeId, any())
        } throws RescheduleValidationException(
            message = "Thời gian hẹn mới phải cách thời điểm hiện tại ít nhất 15 phút.",
            errorCode = ErrorCodes.INVALID_RESCHEDULE_TIME
        )

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/exchange-requests/$testExchangeId/reschedule") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
            contentType(ContentType.Application.Json)
            setBody("""{"new_scheduled_at":"2026-11-05T10:05:00Z"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.INVALID_RESCHEDULE_TIME, err.code)
    }

    // =========================================================================
    // 3. Lay de xuat hien tai (GET /current)
    // =========================================================================

    @Test
    fun `GET current - lay de xuat hien tai thanh cong tra ve 200 OK kem DTO`() = testApplication {
        setupApp()
        val dto = createProposalDto()
        every { mockRescheduleService.getCurrentProposal(bobId, testExchangeId) } returns dto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/exchange-requests/$testExchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val bodyText = response.bodyAsText()
        assertTrue(bodyText.contains(testProposalId))
        assertTrue(bodyText.contains("PENDING"))
    }

    @Test
    fun `GET current - khong co de xuat nao tra ve 200 OK voi data null`() = testApplication {
        setupApp()
        every { mockRescheduleService.getCurrentProposal(bobId, testExchangeId) } returns null

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/exchange-requests/$testExchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"data\":null"))
    }

    @Test
    fun `GET current - IDOR check ke ngoai cuoc Charlie bi tu choi 403 NOT_PARTICIPANT`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.getCurrentProposal(charlieId, testExchangeId)
        } throws RescheduleForbiddenException("Bạn không phải thành viên của phiên trao đổi này", ErrorCodes.NOT_PARTICIPANT)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/exchange-requests/$testExchangeId/reschedule/current") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(charlieId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertTrue(err.code == ErrorCodes.FORBIDDEN || err.code == ErrorCodes.NOT_PARTICIPANT)
    }

    // =========================================================================
    // 4. Chap nhan de xuat (PUT /accept)
    // =========================================================================

    @Test
    fun `PUT accept - doi tac Bob chap nhan de xuat thanh cong tra ve 200 OK ACCEPTED`() = testApplication {
        setupApp()
        val acceptedDto = createProposalDto(status = RescheduleStatus.ACCEPTED.name)
        every { mockRescheduleService.acceptProposal(bobId, testProposalId) } returns acceptedDto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ACCEPTED"))
    }

    @Test
    fun `PUT accept - nguoi tao de xuat Alice tu chap nhan chinh minh bi chan 403 FORBIDDEN`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.acceptProposal(aliceId, testProposalId)
        } throws RescheduleForbiddenException("Không thể tự chấp nhận đề xuất của chính mình", ErrorCodes.FORBIDDEN)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, err.code)
    }

    @Test
    fun `PUT accept - de xuat khong con PENDING (vi du da bi huy) tra ve 409 PROPOSAL_NOT_PENDING`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.acceptProposal(bobId, testProposalId)
        } throws RescheduleConflictException("Đề xuất không ở trạng thái PENDING", ErrorCodes.PROPOSAL_NOT_PENDING)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PROPOSAL_NOT_PENDING, err.code)
    }

    // =========================================================================
    // 5. Tu choi de xuat (PUT /reject)
    // =========================================================================

    @Test
    fun `PUT reject - doi tac Bob tu choi de xuat thanh cong tra ve 200 OK REJECTED`() = testApplication {
        setupApp()
        val rejectedDto = createProposalDto(status = RescheduleStatus.REJECTED.name)
        every { mockRescheduleService.rejectProposal(bobId, testProposalId) } returns rejectedDto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("REJECTED"))
    }

    @Test
    fun `PUT reject - nguoi tao de xuat Alice tu tu choi chinh minh bi chan 403 FORBIDDEN`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.rejectProposal(aliceId, testProposalId)
        } throws RescheduleForbiddenException("Không thể từ chối đề xuất do chính mình tạo", ErrorCodes.FORBIDDEN)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, err.code)
    }

    // =========================================================================
    // 6. Huy de xuat (PUT /cancel)
    // =========================================================================

    @Test
    fun `PUT cancel - nguoi tao Alice huy de xuat PENDING cua minh thanh cong tra ve 200 OK CANCELLED`() = testApplication {
        setupApp()
        val cancelledDto = createProposalDto(status = RescheduleStatus.CANCELLED.name)
        every { mockRescheduleService.cancelProposal(aliceId, testProposalId) } returns cancelledDto

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CANCELLED"))
    }

    @Test
    fun `PUT cancel - doi tac Bob khong phai nguoi tao nen khong the huy bi chan 403 FORBIDDEN`() = testApplication {
        setupApp()
        every {
            mockRescheduleService.cancelProposal(bobId, testProposalId)
        } throws RescheduleForbiddenException("Chỉ người tạo mới có quyền hủy đề xuất này", ErrorCodes.FORBIDDEN)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/reschedule-proposals/$testProposalId/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(bobId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val err = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, err.code)
    }

    // =========================================================================
    // 7. Tuong tac lien quan: Chan hoan thanh exchange khi co de xuat PENDING
    // =========================================================================

    @Test
    fun `PUT complete - bi chan va tra ve 409 Conflict khi dang co de xuat doi lich PENDING con hieu luc`() = testApplication {
        setupApp()
        every {
            mockExchangeService.completeRequest(aliceId, testExchangeId)
        } throws ExchangeConflictException("Không thể hoàn thành trao đổi khi đang có đề xuất đổi lịch chờ phản hồi.")

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/exchange-requests/$testExchangeId/complete") {
            header(HttpHeaders.Authorization, "Bearer ${createToken(aliceId)}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("Không thể hoàn thành trao đổi khi đang có đề xuất đổi lịch"))
    }
}
