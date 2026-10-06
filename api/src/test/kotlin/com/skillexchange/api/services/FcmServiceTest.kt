package com.skillexchange.api.services

import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.services.device.DeviceService
import com.skillexchange.api.services.exchange.FcmExchangeNotificationHook
import com.skillexchange.api.services.notification.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FcmServiceTest {

    private val mockDeviceService = mockk<DeviceService>(relaxed = true)

    private val fakeCredentialsProvider = object : IFcmCredentialsProvider {
        override suspend fun getAccessToken(): String = "fake-oauth-bearer-token"
        override fun getProjectId(): String = "test-firebase-project"
    }

    @BeforeTest
    fun setUp() {
        clearMocks(mockDeviceService)
    }

    @Test
    fun testSendPush_Success_SendsCorrectHeadersAndPayload() = testApplication {
        var receivedAuthHeader: String? = null
        var receivedPayloadText: String? = null

        application {
            install(ServerContentNegotiation) { json() }
            routing {
                post("/v1/projects/test-firebase-project/messages:send") {
                    receivedAuthHeader = call.request.header("Authorization")
                    receivedPayloadText = call.receiveText()
                    call.respond(HttpStatusCode.OK, mapOf("name" to "projects/test/messages/msg-123"))
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val fcmService = FcmService(
            httpClient = client,
            deviceService = mockDeviceService,
            credentialsProvider = fakeCredentialsProvider,
            fcmBaseUrl = ""
        )

        val success = fcmService.sendPushDirect(
            targetToken = "target-device-token-111",
            title = "Yêu cầu trao đổi mới",
            body = "Alice muốn trao đổi Kotlin",
            data = mapOf("type" to "NEW_REQUEST", "entityId" to "req-uuid-1")
        )

        assertTrue(success)
        assertEquals("Bearer fake-oauth-bearer-token", receivedAuthHeader)

        val jsonObj = Json.parseToJsonElement(receivedPayloadText!!).jsonObject["message"]!!.jsonObject
        assertEquals("target-device-token-111", jsonObj["token"]!!.jsonPrimitive.content)

        val notif = jsonObj["notification"]!!.jsonObject
        assertEquals("Yêu cầu trao đổi mới", notif["title"]!!.jsonPrimitive.content)
        assertEquals("Alice muốn trao đổi Kotlin", notif["body"]!!.jsonPrimitive.content)

        val data = jsonObj["data"]!!.jsonObject
        assertEquals("NEW_REQUEST", data["type"]!!.jsonPrimitive.content)
        assertEquals("req-uuid-1", data["entityId"]!!.jsonPrimitive.content)
    }

    @Test
    fun testSendPush_UnregisteredToken_DeletesStaleToken() = testApplication {
        application {
            routing {
                post("/v1/projects/test-firebase-project/messages:send") {
                    call.respondText(
                        status = HttpStatusCode.NotFound,
                        contentType = ContentType.Application.Json,
                        text = """{"error":{"code":404,"message":"Requested entity was not found","status":"NOT_FOUND"}}"""
                    )
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val fcmService = FcmService(
            httpClient = client,
            deviceService = mockDeviceService,
            credentialsProvider = fakeCredentialsProvider,
            fcmBaseUrl = ""
        )

        val result = fcmService.sendPushDirect(
            targetToken = "stale-token-to-delete",
            title = "Yêu cầu trao đổi mới",
            body = "Bob muốn trao đổi Java"
        )

        assertEquals(false, result)
        // Verify token is deleted when FCM returns 404 / NOT_FOUND
        verify(exactly = 1) {
            mockDeviceService.deleteTokenDirect("stale-token-to-delete")
        }
    }

    @Test
    fun testSendPush_ServerError_DoesNotThrowAndDoesNotDeleteToken() = testApplication {
        application {
            routing {
                post("/v1/projects/test-firebase-project/messages:send") {
                    call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val fcmService = FcmService(
            httpClient = client,
            deviceService = mockDeviceService,
            credentialsProvider = fakeCredentialsProvider,
            fcmBaseUrl = ""
        )

        val result = fcmService.sendPushDirect(
            targetToken = "valid-token-temp-fail",
            title = "Yêu cầu",
            body = "Nội dung"
        )

        assertEquals(false, result)
        // Stale token should NOT be deleted on temporary server errors
        verify(exactly = 0) {
            mockDeviceService.deleteTokenDirect("valid-token-temp-fail")
        }
    }

    @Test
    fun testFcmExchangeNotificationHook_BuildsCorrectNotificationFormat() {
        val mockFcmService = mockk<FcmService>(relaxed = true)
        val hook = FcmExchangeNotificationHook(mockFcmService, mockDeviceService)

        val exchangeDto = ExchangeRequestDto(
            id = "exchange-uuid-999",
            senderId = "user-alice",
            receiverId = "user-bob",
            senderName = "Alice Nguyen",
            senderAvatarUrl = null,
            receiverName = "Bob Tran",
            receiverAvatarUrl = null,
            skillOfferedId = 1,
            skillOfferedName = "Lập trình Kotlin",
            skillWantedId = 2,
            skillWantedName = "Thiết kế UI",
            status = "PENDING",
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = "2026-10-15T19:00:00",
            createdAt = "2026-10-06T10:00:00",
            updatedAt = "2026-10-06T10:00:00"
        )

        every { mockDeviceService.getTokensForUser("user-bob") } returns listOf("token-bob-1", "token-bob-2")

        hook.onRequestCreated(exchangeDto)

        // Verify sent to both tokens
        verify(exactly = 1) {
            mockFcmService.sendPushAsync(
                targetToken = "token-bob-1",
                title = "Yêu cầu trao đổi mới",
                body = "Alice Nguyen muốn trao đổi Lập trình Kotlin",
                data = match { it["type"] == "NEW_REQUEST" && it["entityId"] == "exchange-uuid-999" }
            )
        }
        verify(exactly = 1) {
            mockFcmService.sendPushAsync(
                targetToken = "token-bob-2",
                title = "Yêu cầu trao đổi mới",
                body = "Alice Nguyen muốn trao đổi Lập trình Kotlin",
                data = match { it["type"] == "NEW_REQUEST" && it["entityId"] == "exchange-uuid-999" }
            )
        }
    }
}
