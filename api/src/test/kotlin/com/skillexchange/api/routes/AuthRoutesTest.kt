package com.skillexchange.api.routes

import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.AuthService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val mockAuthService = mockk<AuthService>(relaxed = true)

    private fun testAuthApp(block: suspend (client: io.ktor.client.HttpClient) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                authRoutes(mockAuthService)
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
}
