package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.device.DeviceService
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

class DeviceRoutesTest {

    private val mockDeviceService = mockk<DeviceService>(relaxed = true)

    @BeforeTest
    fun setUp() {
        clearMocks(mockDeviceService)
    }

    private fun createTestJwt(userId: String = "user-test-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupDeviceRoutesModule() {
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
                deviceRoutes(mockDeviceService)
            }
        }
    }

    @Test
    fun testRegisterDevice_Success() = testApplication {
        setupDeviceRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = "fcm-token-sample-12345"
        val response = client.post("/api/devices") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token","deviceInfo":"Pixel 8"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Đăng ký thiết bị thành công"))
        verify(exactly = 1) {
            mockDeviceService.registerToken("user-test-123", token, "Pixel 8")
        }
    }

    @Test
    fun testRegisterDevice_BlankToken_ReturnsBadRequest() = testApplication {
        setupDeviceRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.post("/api/devices") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        verify(exactly = 0) { mockDeviceService.registerToken(any(), any(), any()) }
    }

    @Test
    fun testDeleteDevice_QueryParam_Success() = testApplication {
        setupDeviceRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        every { mockDeviceService.deleteToken("user-test-123", "fcm-token-123") } returns true

        val response = client.delete("/api/devices?token=fcm-token-123") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-123")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Xóa thiết bị thành công"))
        verify(exactly = 1) {
            mockDeviceService.deleteToken("user-test-123", "fcm-token-123")
        }
    }

    @Test
    fun testDeleteDevice_Body_Success() = testApplication {
        setupDeviceRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.delete("/api/devices") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-123")}")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"fcm-token-body"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) {
            mockDeviceService.deleteToken("user-test-123", "fcm-token-body")
        }
    }

    @Test
    fun testDeleteDevice_MissingToken_ReturnsBadRequest() = testApplication {
        setupDeviceRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.delete("/api/devices") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-123")}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
