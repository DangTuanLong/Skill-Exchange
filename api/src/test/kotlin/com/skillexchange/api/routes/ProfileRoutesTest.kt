package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.profile.ProfileDto
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.ProfileService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
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
}
