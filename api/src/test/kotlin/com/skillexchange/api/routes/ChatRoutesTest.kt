package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.chat.IChatAttachmentStorageService
import com.skillexchange.api.services.exchange.ExchangeForbiddenException
import com.skillexchange.api.services.exchange.ExchangeService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRoutesTest {

    private val mockStorageService = mockk<IChatAttachmentStorageService>()
    private val mockExchangeService = mockk<ExchangeService>()

    private fun createTestJwt(userId: String = "user-test-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun sampleAcceptedExchange(chatId: String = "exchange-123", status: String = "ACCEPTED"): ExchangeRequestDto {
        return ExchangeRequestDto(
            id = chatId,
            senderId = "user-test-123",
            receiverId = "user-partner-456",
            senderName = "User Test",
            receiverName = "Partner",
            senderAvatarUrl = null,
            receiverAvatarUrl = null,
            skillOfferedId = 1,
            skillOfferedName = "Kotlin",
            skillWantedId = 2,
            skillWantedName = "Design",
            scheduledAt = "2026-10-15T10:00:00Z",
            durationMinutes = 60,
            meetingMode = "ONLINE",
            message = "Trao đổi",
            status = status,
            senderCompletedAt = null,
            receiverCompletedAt = null,
            cancellationReason = null,
            acceptedAt = "2026-10-10T00:00:00Z",
            createdAt = "2026-10-09T00:00:00Z",
            updatedAt = "2026-10-10T00:00:00Z"
        )
    }

    // JPEG magic bytes: FF D8 FF E0 + 10 bytes padding
    private val validJpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    )

    // PDF magic bytes: %PDF-1.4 + padding
    private val validPdfBytes = byteArrayOf(
        0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte(), 0x2D.toByte(),
        0x31, 0x2E, 0x34, 0x0A, 0x25
    )

    private fun testApp(
        rateLimiter: ChatUploadRateLimiter = ChatUploadRateLimiter(),
        block: suspend (client: io.ktor.client.HttpClient) -> Unit
    ) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                chatRoutes(mockStorageService, mockExchangeService, rateLimiter)
            }
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        block(client)
    }

    @Test
    fun `POST attachment without token returns 401 Unauthorized`() = testApp { client ->
        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        )
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("UNAUTHORIZED"))
    }

    @Test
    fun `POST attachment without chatId returns 400 Bad Request`() = testApp { client ->
        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Thiếu thông tin chatId"))
    }

    @Test
    fun `POST attachment when caller is not participant returns 403 Forbidden`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } throws
                ExchangeForbiddenException("Bạn không phải thành viên tham gia yêu cầu này")

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("FORBIDDEN") || text.contains("NOT_PARTICIPANT"))
    }

    @Test
    fun `POST attachment when exchange not found returns 404 Not Found`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns null

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun `POST attachment when exchange status is not ACCEPTED returns 403 Forbidden`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange(status = ExchangeStatus.PENDING.name)

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("FORBIDDEN"))
    }

    @Test
    fun `POST attachment with invalid magic bytes returns 415 Unsupported Media Type`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange()

        val invalidBytes = "This is a plain text file, not image or pdf".toByteArray()

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", invalidBytes, Headers.build {
                    append(HttpHeaders.ContentType, "text/plain")
                    append(HttpHeaders.ContentDisposition, "filename=test.txt")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.bodyAsText().contains("UNSUPPORTED_MEDIA"))
    }

    @Test
    fun `POST attachment when exceeding image 5MB limit returns 413 Payload Too Large`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange()

        // Tạo mảng JPEG vượt quá 5MB
        val largeJpeg = ByteArray(5 * 1024 * 1024 + 100)
        System.arraycopy(validJpegBytes, 0, largeJpeg, 0, validJpegBytes.size)

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", largeJpeg, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=big.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.bodyAsText().contains("PAYLOAD_TOO_LARGE"))
    }

    @Test
    fun `POST attachment when rate limit exceeded returns 429 Too Many Requests`() = testApp(
        rateLimiter = ChatUploadRateLimiter(maxPerMinute = 1, maxPerDay = 10)
    ) { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange()
        coEvery {
            mockStorageService.uploadAttachment("exchange-123", "user-test-123", any(), "jpg", "image/jpeg")
        } returns Result.success("https://r2.example.com/chats/exchange-123/123.jpg")

        // Lần 1: Thành công
        val r1 = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.OK, r1.status)

        // Lần 2: Quá giới hạn (maxPerMinute = 1) -> 429
        val r2 = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=photo2.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.TooManyRequests, r2.status)
        assertTrue(r2.bodyAsText().contains("RATE_LIMITED"))
    }

    @Test
    fun `POST attachment with valid PDF returns 200 OK and attachment response`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange()
        coEvery {
            mockStorageService.uploadAttachment("exchange-123", "user-test-123", any(), "pdf", "application/pdf")
        } returns Result.success("https://r2.example.com/chats/exchange-123/doc.pdf")

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validPdfBytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/pdf")
                    append(HttpHeaders.ContentDisposition, "filename=document.pdf")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("\"success\":true"))
        assertTrue(text.contains("\"type\":\"FILE\""))
        assertTrue(text.contains("\"url\":\"https://r2.example.com/chats/exchange-123/doc.pdf\""))
        assertTrue(text.contains("\"fileName\":\"document.pdf\""))
    }

    @Test
    fun `POST attachment with valid JPEG returns 200 OK and IMAGE type`() = testApp { client ->
        every { mockExchangeService.getExchangeRequest("user-test-123", "exchange-123") } returns
                sampleAcceptedExchange()
        coEvery {
            mockStorageService.uploadAttachment("exchange-123", "user-test-123", any(), "jpg", "image/jpeg")
        } returns Result.success("https://r2.example.com/chats/exchange-123/photo.jpg")

        val response = client.submitFormWithBinaryData(
            url = "/api/chat/attachment",
            formData = formData {
                append("chatId", "exchange-123")
                append("file", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=avatar.jpg")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("\"success\":true"))
        assertTrue(text.contains("\"type\":\"IMAGE\""))
        assertTrue(text.contains("\"fileName\":\"avatar.jpg\""))
    }
}
