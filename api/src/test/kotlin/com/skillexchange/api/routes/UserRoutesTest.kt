package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.UserSearchDto
import com.skillexchange.api.services.UserSearchResultDto
import com.skillexchange.api.services.UserService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRoutesTest {

    private val mockUserService = mockk<UserService>(relaxed = true)

    private fun createTestJwt(userId: String = "user-test-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupUserRoutesModule() {
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
                        if (!credential.payload.subject.isNullOrEmpty()) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                userRoutes(mockUserService)
            }
        }
    }

    @Test
    fun `GET users search clamps limit above 50 down to 50`() = testApplication {
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), 50, any(), any(), any())
        } returns UserSearchResultDto(items = emptyList(), nextCursor = null)

        setupUserRoutesModule()

        val response = client.get("/api/users/search?limit=100")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsersWithCursor(null, emptyList(), null, null, null, null, 50, null, 0, null)
        }
    }

    @Test
    fun `GET users search clamps limit below 1 up to 1`() = testApplication {
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), 1, any(), any(), any())
        } returns UserSearchResultDto(items = emptyList(), nextCursor = null)

        setupUserRoutesModule()

        val response = client.get("/api/users/search?limit=0")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsersWithCursor(null, emptyList(), null, null, null, null, 1, null, 0, null)
        }
    }

    @Test
    fun `GET users search parses categoryIds comma-separated and maxLevel`() = testApplication {
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns UserSearchResultDto(items = emptyList(), nextCursor = null)

        setupUserRoutesModule()

        val response = client.get("/api/users/search?categoryIds=1,2,3&minLevel=2&maxLevel=4")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsersWithCursor(
                query = null,
                categoryIds = listOf(1, 2, 3),
                city = null,
                minProficiency = 2,
                maxProficiency = 4,
                type = null,
                limit = 20,
                lastId = null,
                offset = 0,
                excludeUserId = null
            )
        }
    }

    @Test
    fun `GET users search supports minProficiency and maxProficiency standard parameters`() = testApplication {
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns UserSearchResultDto(items = emptyList(), nextCursor = null)

        setupUserRoutesModule()

        val response = client.get("/api/users/search?minProficiency=2&maxProficiency=4")
        assertEquals(HttpStatusCode.OK, response.status)

        verify {
            mockUserService.searchUsersWithCursor(
                query = null,
                categoryIds = emptyList(),
                city = null,
                minProficiency = 2,
                maxProficiency = 4,
                type = null,
                limit = 20,
                lastId = null,
                offset = 0,
                excludeUserId = null
            )
        }
    }

    @Test
    fun `GET users search returns items and nextCursor`() = testApplication {
        val sampleUser = UserSearchDto(
            userId = "u-456",
            fullName = "Nguyễn Văn B",
            city = "TP.HCM"
        )
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns UserSearchResultDto(items = listOf(sampleUser), nextCursor = "u-456")

        setupUserRoutesModule()

        val response = client.get("/api/users/search?lastId=u-123")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("items"))
        assertTrue(body.contains("nextCursor"))
        assertTrue(body.contains("u-456"))
    }

    @Test
    fun `GET users search excludes caller user id when authenticated`() = testApplication {
        val token = createTestJwt("user-caller-999")
        every {
            mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), "user-caller-999")
        } returns UserSearchResultDto(items = emptyList(), nextCursor = null)

        setupUserRoutesModule()

        val testClient = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = testClient.get("/api/users/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify {
            mockUserService.searchUsersWithCursor(
                query = null,
                categoryIds = emptyList(),
                city = null,
                minProficiency = null,
                maxProficiency = null,
                type = null,
                limit = 20,
                lastId = null,
                offset = 0,
                excludeUserId = "user-caller-999"
            )
        }
    }

    @Test
    fun `GET users search with query nguyen passes query to service and returns matches`() = testApplication {
        val sampleUser = UserSearchDto(
            userId = "u-nguyen",
            fullName = "Nguyễn Văn A",
            city = "Hà Nội"
        )
        every {
            mockUserService.searchUsersWithCursor(
                query = "nguyen",
                categoryIds = emptyList(),
                city = null,
                minProficiency = null,
                maxProficiency = null,
                type = null,
                limit = 20,
                lastId = null,
                offset = 0,
                excludeUserId = null
            )
        } returns UserSearchResultDto(items = listOf(sampleUser), nextCursor = null)

        setupUserRoutesModule()

        val response = client.get("/api/users/search?q=nguyen")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Nguyễn Văn A"))
    }
}
