package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.call.*
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.call.*
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallRoutesTest {

    private val mockCallService = mockk<CallService>(relaxed = true)

    @BeforeTest
    fun setUp() {
        clearMocks(mockCallService)
    }

    private fun createTestJwt(userId: String = "user-test-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupCallRoutesModule() {
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
                callRoutes(mockCallService)
            }
        }
    }

    @Test
    fun testCallsToken_NoJwt_Returns401() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.post("/api/calls/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testInitiateCall_Success_Returns200() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val dummyResp = CallTokenResponse(
            url = "wss://skillexchange.livekit.cloud",
            roomName = "call_abcdef1234567890abcdef1234567890",
            token = "dummy-jwt-token",
            callType = CallType.VIDEO.name,
            sessionId = "session-123"
        )
        coEvery { mockCallService.initiateCall("user-caller", any()) } returns dummyResp

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VIDEO"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("wss://skillexchange.livekit.cloud"))
        assertTrue(body.contains("session-123"))
    }

    @Test
    fun testInitiateCall_NotParticipant_Returns403() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.initiateCall("intruder", any()) } throws CallForbiddenException("NOT_PARTICIPANT", "Bạn không thuộc exchange này")

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("intruder")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("FORBIDDEN") || body.contains("NOT_PARTICIPANT"))
    }

    @Test
    fun testInitiateCall_ExchangeNotActive_Returns409() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.initiateCall("user-caller", any()) } throws CallConflictException("EXCHANGE_NOT_ACTIVE", "Exchange đã kết thúc")

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-completed","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("EXCHANGE_NOT_ACTIVE"))
    }

    @Test
    fun testInitiateCall_UserBusy_Returns409() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.initiateCall("user-caller", any()) } throws CallBusyException("Người dùng đang có cuộc gọi khác")

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("USER_BUSY"))
    }

    @Test
    fun testInitiateCall_CooldownActive_Returns429() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.initiateCall("user-caller", any()) } throws CallRateLimitException("CALL_COOLDOWN_ACTIVE", "Vui lòng đợi 10 giây trước khi gọi lại")

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("RATE_LIMITED") || body.contains("CALL_COOLDOWN_ACTIVE"))
    }

    @Test
    fun testInitiateCall_LiveKitNotConfigured_Returns503() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.initiateCall("user-caller", any()) } throws LiveKitConfigException("Chưa cấu hình LIVEKIT_URL")

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"exchangeId":"ex-123","callType":"VOICE"}""")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("LIVEKIT_NOT_CONFIGURED"))
    }

    @Test
    fun testJoinCall_Success_Returns200() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val dummyResp = CallTokenResponse(
            url = "wss://skillexchange.livekit.cloud",
            roomName = "call_room123",
            token = "dummy-receiver-token",
            callType = CallType.VOICE.name,
            sessionId = "session-123"
        )
        coEvery { mockCallService.joinCall("user-receiver", "session-123") } returns dummyResp

        val response = client.post("/api/calls/token/join") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-receiver")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("dummy-receiver-token"))
    }

    @Test
    fun testAcceptConnected_Success_Returns200() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val dummySession = CallSessionDto(
            id = "session-123",
            exchangeId = "ex-123",
            callerId = "user-caller",
            receiverId = "user-receiver",
            roomName = "call_room123",
            callType = CallType.VOICE.name,
            status = CallStatus.CONNECTED.name,
            startedAt = "2026-10-27T08:00:00Z",
            connectedAt = "2026-10-27T08:00:05Z",
            endedAt = null,
            durationSeconds = 0
        )
        coEvery { mockCallService.acceptConnected("user-receiver", "session-123") } returns dummySession

        val response = client.post("/api/calls/connected") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-receiver")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CONNECTED"))
    }

    @Test
    fun testAcceptConnected_CallerForbidden_Returns403() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.acceptConnected("user-caller", "session-123") } throws CallForbiddenException(
            "ONLY_RECEIVER_CAN_CONNECT",
            "Chỉ người nhận mới có quyền xác nhận kết nối cuộc gọi"
        )

        val response = client.post("/api/calls/connected") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("FORBIDDEN") || body.contains("ONLY_RECEIVER_CAN_CONNECT"))
    }

    @Test
    fun testRejectCall_Success_Returns200() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val dummySession = CallSessionDto(
            id = "session-123",
            exchangeId = "ex-123",
            callerId = "user-caller",
            receiverId = "user-receiver",
            roomName = "call_room123",
            callType = CallType.VOICE.name,
            status = CallStatus.REJECTED.name,
            startedAt = "2026-10-27T08:00:00Z",
            connectedAt = null,
            endedAt = "2026-10-27T08:00:05Z",
            durationSeconds = 0
        )
        coEvery { mockCallService.rejectCall("user-receiver", "session-123") } returns dummySession

        val response = client.post("/api/calls/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-receiver")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("REJECTED"))
    }

    @Test
    fun testEndCall_Success_Returns200() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val dummySession = CallSessionDto(
            id = "session-123",
            exchangeId = "ex-123",
            callerId = "user-caller",
            receiverId = "user-receiver",
            roomName = "call_room123",
            callType = CallType.VOICE.name,
            status = CallStatus.ENDED.name,
            startedAt = "2026-10-27T08:00:00Z",
            connectedAt = "2026-10-27T08:00:05Z",
            endedAt = "2026-10-27T08:05:05Z",
            durationSeconds = 300
        )
        coEvery { mockCallService.endCall("user-caller", "session-123") } returns dummySession

        val response = client.post("/api/calls/end") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ENDED"))
        assertTrue(body.contains("300"))
    }

    @Test
    fun testEndCall_NonParticipantForbidden_Returns403() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        coEvery { mockCallService.endCall("stranger", "session-123") } throws CallForbiddenException(
            "NOT_CALL_PARTICIPANT",
            "Bạn không phải người tham gia cuộc gọi này"
        )

        val response = client.post("/api/calls/end") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("stranger")}")
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-123"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("FORBIDDEN") || body.contains("NOT_CALL_PARTICIPANT"))
    }

    @Test
    fun testCallRoutes_InvalidBody_Returns400() = testApplication {
        setupCallRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.post("/api/calls/token") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-caller")}")
            contentType(ContentType.Application.Json)
            setBody("invalid-non-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("BAD_REQUEST"))
    }
}
