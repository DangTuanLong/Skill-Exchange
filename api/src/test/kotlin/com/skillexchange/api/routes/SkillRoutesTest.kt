package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.SkillService
import com.skillexchange.api.services.UserService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillRoutesTest {

    private val mockSkillService = mockk<SkillService>(relaxed = true)
    private val mockUserService = mockk<UserService>(relaxed = true)

    private fun createTestJwt(): String {
        return JWT.create()
            .withSubject("user-test-123")
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.application.Application.setupSkillTestModule() {
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
            skillRoutes(mockSkillService, mockUserService)
        }
    }

    @Test
    fun `POST user-skill with invalid type returns 400 Bad Request`() = testApplication {
        val testToken = createTestJwt()

        application {
            setupSkillTestModule()
        }

        val testClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = testClient.post("/api/skills/user") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"skill_id":1,"type":"INVALID","proficiency_level":3}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("type phải là HAVE hoặc WANT"))
    }

    @Test
    fun `GET skills search clamps limit 0 up to 1`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), any(), any(), 1, any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response = client.get("/api/skills/search?limit=0")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockUserService.searchUsers(null, null, null, null, null, 1, 0) }
    }

    @Test
    fun `GET skills search limit 1 passes 1`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), any(), any(), 1, any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response = client.get("/api/skills/search?limit=1")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockUserService.searchUsers(null, null, null, null, null, 1, 0) }
    }

    @Test
    fun `GET skills search limit 50 passes 50`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), any(), any(), 50, any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response = client.get("/api/skills/search?limit=50")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockUserService.searchUsers(null, null, null, null, null, 50, 0) }
    }

    @Test
    fun `GET skills search clamps limit 51 down to 50`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), any(), any(), 50, any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response = client.get("/api/skills/search?limit=51")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockUserService.searchUsers(null, null, null, null, null, 50, 0) }
    }

    @Test
    fun `GET skills search clamps limit 1000 down to 50`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), any(), any(), 50, any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response = client.get("/api/skills/search?limit=1000")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockUserService.searchUsers(null, null, null, null, null, 50, 0) }
    }

    @Test
    fun `GET skills search supports minProficiency and legacy minLevel alias`() = testApplication {
        every { mockUserService.searchUsers(any(), any(), any(), 4, any(), any(), any()) } returns emptyList()

        application {
            setupSkillTestModule()
        }

        val response1 = client.get("/api/skills/search?minProficiency=4")
        assertEquals(HttpStatusCode.OK, response1.status)

        val response2 = client.get("/api/skills/search?minLevel=4")
        assertEquals(HttpStatusCode.OK, response2.status)

        verify(exactly = 2) { mockUserService.searchUsers(null, null, null, 4, null, 20, 0) }
    }
}
