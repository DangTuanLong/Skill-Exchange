package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.skill.UserSkillDto
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.SkillService
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
            skillRoutes(mockSkillService)
        }
    }

    @Test
    fun `POST user-skill with invalid type returns 422 Unprocessable Entity`() = testApplication {
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

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("type phải là HAVE hoặc WANT"))
    }

    @Test
    fun `POST user-skill with proficiency 0 returns 422 Unprocessable Entity`() = testApplication {
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
            setBody("""{"skill_id":1,"type":"HAVE","proficiency_level":0}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Trình độ kỹ năng phải từ 1 đến 5"))
    }

    @Test
    fun `POST user-skill with proficiency 6 returns 422 Unprocessable Entity`() = testApplication {
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
            setBody("""{"skill_id":1,"type":"HAVE","proficiency_level":6}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Trình độ kỹ năng phải từ 1 đến 5"))
    }

    @Test
    fun `POST user-skill with boundary proficiency 1 succeeds with 201 Created`() = testApplication {
        val testToken = createTestJwt()
        val sampleSkill = UserSkillDto(
            id = "us-1",
            skillId = 1,
            skillName = "Kotlin",
            categoryName = "Lập trình",
            type = "HAVE",
            proficiencyLevel = 1
        )
        every { mockSkillService.addUserSkill("user-test-123", any()) } returns sampleSkill

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
            setBody("""{"skill_id":1,"type":"HAVE","proficiency_level":1}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST user-skill with boundary proficiency 5 succeeds with 201 Created`() = testApplication {
        val testToken = createTestJwt()
        val sampleSkill = UserSkillDto(
            id = "us-2",
            skillId = 1,
            skillName = "Kotlin",
            categoryName = "Lập trình",
            type = "WANT",
            proficiencyLevel = 5
        )
        every { mockSkillService.addUserSkill("user-test-123", any()) } returns sampleSkill

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
            setBody("""{"skill_id":1,"type":"WANT","proficiency_level":5}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }
}
