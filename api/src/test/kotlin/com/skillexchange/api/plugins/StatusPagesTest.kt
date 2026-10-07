package com.skillexchange.api.plugins

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusPagesTest {

    private fun testStatusPagesApp(block: suspend (client: io.ktor.client.HttpClient) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                get("/api/auth/login") {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                get("/test/unauthorized") {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                get("/test/forbidden") {
                    call.respond(HttpStatusCode.Forbidden)
                }
                get("/test/not-found") {
                    call.respond(HttpStatusCode.NotFound)
                }
                get("/test/payload-too-large") {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                }
                get("/test/unsupported-media") {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
                get("/test/rate-limited") {
                    call.respond(HttpStatusCode.TooManyRequests)
                }
                get("/test/validation-exception") {
                    throw ValidationException(
                        message = "Dữ liệu không hợp lệ",
                        fieldErrors = mapOf("email" to "Email không đúng định dạng")
                    )
                }
                get("/test/illegal-argument") {
                    throw IllegalArgumentException("Tham số không hợp lệ")
                }
                get("/test/internal-error") {
                    throw RuntimeException("DATABASE_PASSWORD=secret123 connection failed!")
                }
            }
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        block(client)
    }

    @Test
    fun `ErrorCodes fromStatus mappings match specification`() {
        assertEquals(ErrorCodes.BAD_REQUEST, ErrorCodes.fromStatus(400))
        assertEquals(ErrorCodes.UNAUTHORIZED, ErrorCodes.fromStatus(401))
        assertEquals(ErrorCodes.FORBIDDEN, ErrorCodes.fromStatus(403))
        assertEquals(ErrorCodes.NOT_FOUND, ErrorCodes.fromStatus(404))
        assertEquals(ErrorCodes.CONFLICT, ErrorCodes.fromStatus(409))
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, ErrorCodes.fromStatus(413))
        assertEquals(ErrorCodes.UNSUPPORTED_MEDIA, ErrorCodes.fromStatus(415))
        assertEquals(ErrorCodes.VALIDATION_FAILED, ErrorCodes.fromStatus(422))
        assertEquals(ErrorCodes.RATE_LIMITED, ErrorCodes.fromStatus(429))
        assertEquals(ErrorCodes.INTERNAL, ErrorCodes.fromStatus(500))
        assertEquals(ErrorCodes.INTERNAL, ErrorCodes.fromStatus(502))
    }

    @Test
    fun `IllegalArgumentException returns 400 Bad Request with BAD_REQUEST code`() = testStatusPagesApp { client ->
        val response = client.get("/test/illegal-argument")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(400, body.status)
        assertEquals(ErrorCodes.BAD_REQUEST, body.code)
        assertEquals("Tham số không hợp lệ", body.message)
    }

    @Test
    fun `401 on login endpoint returns UNAUTHORIZED with auth-specific message`() = testStatusPagesApp { client ->
        val response = client.get("/api/auth/login")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(401, body.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, body.code)
        assertEquals("Email hoặc mật khẩu không đúng", body.message)
    }

    @Test
    fun `401 on standard endpoint returns UNAUTHORIZED with login prompt`() = testStatusPagesApp { client ->
        val response = client.get("/test/unauthorized")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(401, body.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, body.code)
        assertEquals("Chưa xác thực. Vui lòng đăng nhập.", body.message)
    }

    @Test
    fun `403 Forbidden returns FORBIDDEN ApiError`() = testStatusPagesApp { client ->
        val response = client.get("/test/forbidden")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(403, body.status)
        assertEquals(ErrorCodes.FORBIDDEN, body.code)
        assertEquals("Không có quyền truy cập.", body.message)
    }

    @Test
    fun `404 Not Found returns NOT_FOUND ApiError`() = testStatusPagesApp { client ->
        val response = client.get("/test/not-found")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(404, body.status)
        assertEquals(ErrorCodes.NOT_FOUND, body.code)
        assertEquals("Không tìm thấy tài nguyên yêu cầu.", body.message)
    }

    @Test
    fun `413 Payload Too Large returns PAYLOAD_TOO_LARGE ApiError`() = testStatusPagesApp { client ->
        val response = client.get("/test/payload-too-large")
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(413, body.status)
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, body.code)
        assertEquals("Kích thước tập tin vượt quá giới hạn cho phép.", body.message)
    }

    @Test
    fun `415 Unsupported Media Type returns UNSUPPORTED_MEDIA ApiError`() = testStatusPagesApp { client ->
        val response = client.get("/test/unsupported-media")
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(415, body.status)
        assertEquals(ErrorCodes.UNSUPPORTED_MEDIA, body.code)
        assertEquals("Định dạng tập tin không được hỗ trợ.", body.message)
    }

    @Test
    fun `422 ValidationException returns VALIDATION_FAILED with fieldErrors`() = testStatusPagesApp { client ->
        val response = client.get("/test/validation-exception")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(422, body.status)
        assertEquals(ErrorCodes.VALIDATION_FAILED, body.code)
        assertEquals("Dữ liệu không hợp lệ", body.message)
        assertEquals("Email không đúng định dạng", body.fieldErrors?.get("email"))
    }

    @Test
    fun `429 Too Many Requests returns RATE_LIMITED ApiError`() = testStatusPagesApp { client ->
        val response = client.get("/test/rate-limited")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(429, body.status)
        assertEquals(ErrorCodes.RATE_LIMITED, body.code)
        assertEquals("Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút.", body.message)
    }

    @Test
    fun `500 InternalServerError masks raw exception message and stack trace from client`() = testStatusPagesApp { client ->
        val response = client.get("/test/internal-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.body<ApiError>()
        assertFalse(body.success)
        assertEquals(500, body.status)
        assertEquals(ErrorCodes.INTERNAL, body.code)
        assertEquals("Lỗi máy chủ. Vui lòng thử lại sau.", body.message)
        assertNull(body.fieldErrors)
        // Ensure sensitive secrets are NEVER leaked
        assertFalse(body.message.contains("DATABASE_PASSWORD"))
        assertFalse(body.message.contains("secret123"))
    }
}
