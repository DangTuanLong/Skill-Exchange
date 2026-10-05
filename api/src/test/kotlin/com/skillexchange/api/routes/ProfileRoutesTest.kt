package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.profile.ProfileDto
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.IAvatarStorageService
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.ProfileService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
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
import kotlin.test.assertTrue

class ProfileRoutesTest {

    private val mockProfileService = mockk<ProfileService>()

    private fun createTestJwt(): String {
        return JWT.create()
            .withSubject("user-test-123")
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    @Test
    fun `GET profile me without token returns 401 Unauthorized`() = testApplication {
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
                profileRoutes(mockProfileService)
            }
        }

        val response = client.get("/api/profile/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Chưa xác thực"))
    }

    @Test
    fun `PUT profile with blank fullName returns 422 Unprocessable Entity`() = testApplication {
        val testToken = createTestJwt()

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
                profileRoutes(mockProfileService)
            }
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"full_name":"","bio":"Dev","city":"HCM"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Họ tên không được để trống"))
    }

    @Test
    fun `PUT profile with bio 500 characters succeeds with 200 OK`() = testApplication {
        val testToken = createTestJwt()
        val bio500 = "a".repeat(500)
        val sampleProfile = ProfileDto(
            id = "p-123",
            userId = "user-test-123",
            fullName = "Nguyễn Văn A",
            bio = bio500,
            city = "HCM"
        )
        every { mockProfileService.upsertProfile("user-test-123", any()) } returns sampleProfile

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
                profileRoutes(mockProfileService)
            }
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"full_name":"Nguyễn Văn A","bio":"$bio500","city":"HCM"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Nguyễn Văn A"))
    }

    @Test
    fun `PUT profile with bio 501 characters returns 422 Unprocessable Entity`() = testApplication {
        val testToken = createTestJwt()
        val bio501 = "a".repeat(501)

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
                profileRoutes(mockProfileService)
            }
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"full_name":"Nguyễn Văn A","bio":"$bio501","city":"HCM"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Tiểu sử không được vượt quá 500 ký tự"))
    }

    @Test
    fun `GET profile userId returns public profile when exists`() = testApplication {
        val sampleProfile = ProfileDto(
            id = "p-123",
            userId = "user-123",
            fullName = "Nguyễn Văn A",
            bio = "Lập trình viên",
            city = "Hà Nội"
        )
        every { mockProfileService.getProfile("user-123") } returns sampleProfile

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { null }
                }
            }
            routing {
                profileRoutes(mockProfileService)
            }
        }

        val response = client.get("/api/profile/user-123")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Nguyễn Văn A"))
    }

    @Test
    fun `GET profile userId returns 404 when profile not found`() = testApplication {
        every { mockProfileService.getProfile("nonexistent") } returns null

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { null }
                }
            }
            routing {
                profileRoutes(mockProfileService)
            }
        }

        val response = client.get("/api/profile/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("Không tìm thấy") || response.bodyAsText().contains("Profile not found"))
    }

    @Test
    fun `PUT profile with invalid day returns 422 Unprocessable Entity`() = testApplication {
        val testToken = createTestJwt()

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(
                com.skillexchange.api.models.profile.UpdateProfileRequest(
                    fullName = "Nguyễn Văn A",
                    availability = listOf(
                        com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "FUNDAY", from = "18:00", to = "20:00")
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("không hợp lệ (phải thuộc MON..SUN)"))
    }

    @Test
    fun `PUT profile with invalid time format returns 422`() = testApplication {
        val testToken = createTestJwt()

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(
                com.skillexchange.api.models.profile.UpdateProfileRequest(
                    fullName = "Nguyễn Văn A",
                    availability = listOf(
                        com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "8:00", to = "20:00")
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Định dạng giờ không hợp lệ"))
    }

    @Test
    fun `PUT profile with from greater than or equal to to returns 422`() = testApplication {
        val testToken = createTestJwt()

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(
                com.skillexchange.api.models.profile.UpdateProfileRequest(
                    fullName = "Nguyễn Văn A",
                    availability = listOf(
                        com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "18:00", to = "18:00")
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Giờ bắt đầu") && response.bodyAsText().contains("phải sớm hơn giờ kết thúc"))
    }

    @Test
    fun `PUT profile with overlapping windows on same day returns 422`() = testApplication {
        val testToken = createTestJwt()

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(
                com.skillexchange.api.models.profile.UpdateProfileRequest(
                    fullName = "Nguyễn Văn A",
                    availability = listOf(
                        com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "08:00", to = "12:00"),
                        com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "11:00", to = "15:00")
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("bị chồng nhau"))
    }

    @Test
    fun `PUT profile with valid non-overlapping availability returns 200`() = testApplication {
        val testToken = createTestJwt()
        val validWindows = listOf(
            com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "08:00", to = "12:00"),
            com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "MON", from = "12:00", to = "16:00"), // adjacent is allowed
            com.skillexchange.api.models.profile.AvailabilityWindowDto(day = "WED", from = "18:00", to = "21:00")
        )

        every {
            mockProfileService.upsertProfile(
                "user-test-123",
                match { it.fullName == "Nguyễn Văn A" && it.availability?.size == 3 }
            )
        } returns ProfileDto(
            id = "p-1",
            userId = "user-test-123",
            fullName = "Nguyễn Văn A",
            availability = validWindows
        )

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(
                com.skillexchange.api.models.profile.UpdateProfileRequest(
                    fullName = "Nguyễn Văn A",
                    availability = validWindows
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Nguyễn Văn A"))
        assertTrue(response.bodyAsText().contains("WED"))
    }

    // ─── POST /api/profile/avatar Tests ────────────────────────────────────

    class FakeAvatarStorageService(
        var uploadResult: Result<String> = Result.success("https://r2.example.com/avatars/user-test-123_1710000000_abc123.jpg"),
        val uploadedFiles: MutableList<Triple<String, ByteArray, String>> = mutableListOf(),
        val deletedKeys: MutableList<String> = mutableListOf()
    ) : IAvatarStorageService {
        override suspend fun uploadAvatar(userId: String, bytes: ByteArray, extension: String, mimeType: String): Result<String> {
            uploadedFiles.add(Triple(userId, bytes, mimeType))
            return uploadResult
        }

        override suspend fun deleteAvatar(objectKey: String): Result<Unit> {
            deletedKeys.add(objectKey)
            return Result.success(Unit)
        }
    }

    @Test
    fun `POST profile avatar without token returns 401 Unauthorized`() = testApplication {
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter()
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { null }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val response = client.post("/api/profile/avatar")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST profile avatar with payload greater than 2MB returns 413 Payload Too Large`() = testApplication {
        val testToken = createTestJwt()
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter()
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val largeBytes = ByteArray(2 * 1024 * 1024 + 10) { 0 }
        val response = testClient.submitFormWithBinaryData(
            url = "/api/profile/avatar",
            formData = formData {
                append("avatar", largeBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"large.jpg\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.bodyAsText().contains("vượt quá giới hạn 2MB"))
    }

    @Test
    fun `POST profile avatar with invalid magic bytes returns 415 Unsupported Media Type`() = testApplication {
        val testToken = createTestJwt()
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter()
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val corruptBytes = "NotAnImageFileContentAtAll".toByteArray()
        val response = testClient.submitFormWithBinaryData(
            url = "/api/profile/avatar",
            formData = formData {
                append("avatar", corruptBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"fake.jpg\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.bodyAsText().contains("không được hỗ trợ"))
    }

    @Test
    fun `POST profile avatar with missing file part returns 400 Bad Request`() = testApplication {
        val testToken = createTestJwt()
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter()
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val response = testClient.submitFormWithBinaryData(
            url = "/api/profile/avatar",
            formData = formData {
                append("some_text_field", "hello")
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Không tìm thấy file ảnh"))
    }

    @Test
    fun `POST profile avatar with valid JPEG succeeds, updates DB, deletes old avatar, and returns 200`() = testApplication {
        val testToken = createTestJwt()
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter()
        val oldUrl = "https://r2.example.com/avatars/user-test-123_old_987654.jpg"
        every { mockProfileService.updateAvatarUrl("user-test-123", any()) } returns oldUrl

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        // Valid JPEG header bytes: FF D8 FF E0 00 10 4A 46 49 46 00 01
        val validJpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x00, 0x00
        )
        val response = testClient.submitFormWithBinaryData(
            url = "/api/profile/avatar",
            formData = formData {
                append("avatar", validJpegBytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("user-test-123"))
        assertEquals(1, fakeStorage.uploadedFiles.size)
        assertEquals("image/jpeg", fakeStorage.uploadedFiles.first().third)
        assertEquals(1, fakeStorage.deletedKeys.size)
        assertEquals("avatars/user-test-123_old_987654.jpg", fakeStorage.deletedKeys.first())
    }

    @Test
    fun `POST profile avatar exceeding rate limit returns 429 Too Many Requests`() = testApplication {
        val testToken = createTestJwt()
        val fakeStorage = FakeAvatarStorageService()
        val rateLimiter = InMemoryRateLimiter(maxRequests = 2) // limit to 2 for quick testing
        every { mockProfileService.updateAvatarUrl("user-test-123", any()) } returns null

        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "Test Realm"
                    verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { profileRoutes(mockProfileService, fakeStorage, rateLimiter) }
        }

        val testClient = createClient { install(ContentNegotiation) { json() } }
        val validJpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x00, 0x00
        )

        // 1st request
        val r1 = testClient.submitFormWithBinaryData(url = "/api/profile/avatar", formData = formData {
            append("avatar", validJpegBytes, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"a.jpg\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $testToken") }
        assertEquals(HttpStatusCode.OK, r1.status)

        // 2nd request
        val r2 = testClient.submitFormWithBinaryData(url = "/api/profile/avatar", formData = formData {
            append("avatar", validJpegBytes, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"a.jpg\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $testToken") }
        assertEquals(HttpStatusCode.OK, r2.status)

        // 3rd request -> 429
        val r3 = testClient.submitFormWithBinaryData(url = "/api/profile/avatar", formData = formData {
            append("avatar", validJpegBytes, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"a.jpg\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $testToken") }
        assertEquals(HttpStatusCode.TooManyRequests, r3.status)
        assertTrue(r3.bodyAsText().contains("quá nhiều"))
    }

}

