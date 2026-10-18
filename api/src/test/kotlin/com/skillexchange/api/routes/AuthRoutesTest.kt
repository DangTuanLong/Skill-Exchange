package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.auth.IFirebaseTokenService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val mockAuthService = mockk<AuthService>(relaxed = true)
    private val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)

    private fun createTestJwt(userId: String = "test-user-uuid-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-jwt-secret"))
    }

    private fun testAuthApp(
        authRateLimiter: com.skillexchange.api.services.auth.AuthRateLimiter = com.skillexchange.api.services.auth.AuthRateLimiter(),
        block: suspend (client: io.ktor.client.HttpClient) -> Unit
    ) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "SkillExchange API"
                    verifier(
                        JWT.require(Algorithm.HMAC256("test-jwt-secret"))
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
                authRoutes(mockAuthService, mockFirebaseTokenService, authRateLimiter)
            }
        }
        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        block(testClient)
    }

    @Test
    fun `POST register with blank email returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"","password":"password123","full_name":"Test User"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Email không hợp lệ"))
    }

    @Test
    fun `POST register with invalid email regex returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"invalidemail","password":"password123","full_name":"Test User"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Email không hợp lệ"))
    }

    @Test
    fun `POST register with short password returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"123","full_name":"Test User"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Mật khẩu phải có ít nhất 6 ký tự"))
    }

    @Test
    fun `POST register with blank fullName returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"password123","full_name":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Họ tên không được để trống"))
    }

    @Test
    fun `POST login with blank credentials returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"","password":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Email và mật khẩu không được để trống"))
    }

    @Test
    fun `POST login with invalid credentials returns 401 Unauthorized with unified ApiError`() {
        io.mockk.coEvery { mockAuthService.login("wrong@example.com", "wrongpass") } throws RuntimeException("Invalid credentials")
        testAuthApp { client ->
            val response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"wrong@example.com","password":"wrongpass"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"code\":\"UNAUTHORIZED\""))
            assertTrue(body.contains("\"status\":401"))
            assertTrue(body.contains("\"message\":\"Email hoặc mật khẩu không đúng\""))
        }
    }

    @Test
    fun `POST verify-otp with invalid token length returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","token":"123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Mã OTP phải từ 6 đến 8 chữ số"))
    }

    @Test
    fun `POST refresh with blank token returns 400 Bad Request`() = testAuthApp { client ->
        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("refresh_token không được để trống"))
    }

    @Test
    fun `POST firebase-token without JWT returns 401 Unauthorized`() = testAuthApp { client ->
        val response = client.post("/api/auth/firebase-token")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST firebase-token with valid JWT returns custom token`() {
        val userId = "test-user-uuid-123"
        val expectedCustomToken = "mock.firebase.custom.jwt.token"
        every { mockFirebaseTokenService.createCustomToken(userId) } returns expectedCustomToken

        testAuthApp { client ->
            val jwt = createTestJwt(userId)
            val response = client.post("/api/auth/firebase-token") {
                header(HttpHeaders.Authorization, "Bearer $jwt")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(expectedCustomToken))
            assertTrue(body.contains("\"expiresIn\":3600"))
        }
    }

    @Test
    fun `POST register with password longer than 256 characters returns 422 Unprocessable Entity`() = testAuthApp { client ->
        val longPassword = "a".repeat(257)
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"$longPassword","full_name":"Test User"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("VALIDATION_FAILED"))
        assertTrue(body.contains("Mật khẩu không được vượt quá 256 ký tự"))
    }

    @Test
    fun `POST register with email longer than 254 characters returns 422 Unprocessable Entity`() = testAuthApp { client ->
        val longEmail = "a".repeat(250) + "@test.com" // > 254
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$longEmail","password":"password123","full_name":"Test User"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("VALIDATION_FAILED"))
        assertTrue(body.contains("Email không được vượt quá 254 ký tự"))
    }

    @Test
    fun `POST register with full_name longer than 100 characters returns 422 Unprocessable Entity`() = testAuthApp { client ->
        val longName = "a".repeat(101)
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"password123","full_name":"$longName"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("VALIDATION_FAILED"))
        assertTrue(body.contains("Họ tên không được vượt quá 100 ký tự"))
    }

    @Test
    fun `POST login with password longer than 256 characters returns 422 Unprocessable Entity`() = testAuthApp { client ->
        val longPassword = "a".repeat(257)
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"$longPassword"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("VALIDATION_FAILED"))
        assertTrue(body.contains("Mật khẩu không được vượt quá 256 ký tự"))
    }

    @Test
    fun `POST login rate limit returns 429 Too Many Requests when exceeding limit`() {
        val testLimiter = com.skillexchange.api.services.auth.AuthRateLimiter(maxLoginPerMinute = 2)
        testAuthApp(authRateLimiter = testLimiter) { client ->
            // 2 allowed attempts
            client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"rate@example.com","password":"password123"}""")
            }
            client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"rate@example.com","password":"password123"}""")
            }

            // 3rd attempt is rate-limited
            val response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"rate@example.com","password":"password123"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("RATE_LIMITED"))
            assertTrue(body.contains("429"))
        }
    }

    @Test
    fun `POST register server error does not leak raw exception message`() {
        io.mockk.coEvery {
            mockAuthService.register(any(), any(), any())
        } throws RuntimeException("FATAL: relation \"auth.users\" does not exist at postgres_backend.c:1234")

        testAuthApp { client ->
            val response = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"leak@example.com","password":"password123","full_name":"Test"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            // Ensure internal DB error stack is not leaked
            assertFalse(body.contains("postgres_backend"))
            assertFalse(body.contains("FATAL"))
            assertTrue(body.contains("Đăng ký thất bại"))
        }
    }
}
