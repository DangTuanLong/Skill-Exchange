package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsRequest
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.nlp.NlpRateLimiter
import com.skillexchange.api.services.nlp.SkillDefinition
import com.skillexchange.api.services.nlp.SkillExtractionService
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NlpRoutesTest {

    private val testSkills = listOf(
        SkillDefinition(1, "Android (Kotlin)", 1, "Lập trình"),
        SkillDefinition(2, "Python", 1, "Lập trình"),
        SkillDefinition(3, "Java", 1, "Lập trình"),
        SkillDefinition(4, "JavaScript", 1, "Lập trình"),
        SkillDefinition(5, "C", 1, "Lập trình"),
        SkillDefinition(6, "C++", 1, "Lập trình"),
        SkillDefinition(7, "C#", 1, "Lập trình"),
        SkillDefinition(8, "Tiếng Anh", 2, "Ngoại ngữ"),
        SkillDefinition(9, "Guitar", 3, "Âm nhạc"),
        SkillDefinition(10, "Adobe Photoshop", 4, "Thiết kế")
    )

    private val skillExtractionService = SkillExtractionService(skillProvider = { testSkills })
    private val rateLimiter = NlpRateLimiter(maxPerMinute = 5)

    private fun createTestJwt(userId: String = "test-user-uuid-1"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.application.Application.setupNlpTestModule() {
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
            nlpRoutes(skillExtractionService, rateLimiter)
        }
    }

    @Test
    fun `POST extract-skills returns 401 when no token provided`() = testApplication {
        application { setupNlpTestModule() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.post("/api/nlp/extract-skills") {
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = "Tôi biết lập trình Python"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST extract-skills returns 422 when bio text is blank`() = testApplication {
        application { setupNlpTestModule() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.post("/api/nlp/extract-skills") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = "   "))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, body.code)
    }

    @Test
    fun `POST extract-skills returns 422 when bio text exceeds 500 characters`() = testApplication {
        application { setupNlpTestModule() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val longText = "a".repeat(501)
        val response = client.post("/api/nlp/extract-skills") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = longText))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, body.code)
    }

    @Test
    fun `POST extract-skills returns 429 when rate limit exceeded`() = testApplication {
        application { setupNlpTestModule() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val userId = "rate-limited-user"
        val token = createTestJwt(userId)

        // Thực hiện 5 request liên tiếp (maxPerMinute = 5)
        repeat(5) {
            val res = client.post("/api/nlp/extract-skills") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(ExtractSkillsRequest(text = "Tôi học Python"))
            }
            assertEquals(HttpStatusCode.OK, res.status)
        }

        // Request thứ 6 phải trả về 429
        val resOver = client.post("/api/nlp/extract-skills") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = "Tôi học Python"))
        }
        assertEquals(HttpStatusCode.TooManyRequests, resOver.status)
        val body = Json.decodeFromString<ApiError>(resOver.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, body.code)
    }

    @Test
    fun `POST extract-skills extracts skills with confidence tiers`() = testApplication {
        application { setupNlpTestModule() }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // Exact: Python (1.0), Alias: ps -> Adobe Photoshop (0.9), Fuzzy: pythn -> Python (0.75 nếu có)
        val response = client.post("/api/nlp/extract-skills") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("user-test-2")}")
            contentType(ContentType.Application.Json)
            setBody(ExtractSkillsRequest(text = "Mình có kinh nghiệm lập trình Python và thiết kế bằng ps."))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)

        val pythonSkill = body.data.find { it.skillId == 2 }
        val psSkill = body.data.find { it.skillId == 10 }

        assertTrue(pythonSkill != null, "Phải tìm thấy Python")
        assertEquals(1.0, pythonSkill.confidence)
        assertEquals("EXACT", pythonSkill.matchType)

        assertTrue(psSkill != null, "Phải tìm thấy Photoshop từ alias ps")
        assertEquals(0.9, psSkill.confidence)
        assertEquals("ALIAS", psSkill.matchType)
    }

    // ─── Word Boundary Test Cases (Requirement 2) ──────────────────────────

    @Test
    fun `word boundary test - does NOT match Java inside JavaScript`() {
        val bio1 = "Mình chuyên lập trình JavaScript và phát triển website"
        val extracted1 = skillExtractionService.extractSkills(bio1)
        val skillIds1 = extracted1.map { it.skillId }

        assertTrue(skillIds1.contains(4), "Phải bắt được JavaScript (id=4)")
        assertFalse(skillIds1.contains(3), "KHÔNG ĐƯỢC bắt nhầm Java (id=3) khi chuỗi là JavaScript")

        val bio2 = "Mình làm backend bằng Java Spring Boot"
        val extracted2 = skillExtractionService.extractSkills(bio2)
        val skillIds2 = extracted2.map { it.skillId }

        assertTrue(skillIds2.contains(3), "Phải bắt được Java (id=3) khi từ đứng độc lập")
        assertFalse(skillIds2.contains(4), "Không được bắt JavaScript khi chỉ có Java")
    }

    @Test
    fun `word boundary test - does NOT match C inside C++ or C#`() {
        val bio1 = "Lập trình C++ và C# nâng cao, xây dựng hệ thống nhúng"
        val extracted1 = skillExtractionService.extractSkills(bio1)
        val skillIds1 = extracted1.map { it.skillId }

        assertTrue(skillIds1.contains(6), "Phải bắt được C++ (id=6)")
        assertTrue(skillIds1.contains(7), "Phải bắt được C# (id=7)")
        assertFalse(skillIds1.contains(5), "KHÔNG ĐƯỢC bắt nhầm C (id=5) khi chuỗi là C++ hoặc C#")

        val bio2 = "Mình học ngôn ngữ C cơ bản tại đại học"
        val extracted2 = skillExtractionService.extractSkills(bio2)
        val skillIds2 = extracted2.map { it.skillId }

        assertTrue(skillIds2.contains(5), "Phải bắt được C (id=5) khi từ đứng độc lập")
        assertFalse(skillIds2.contains(6), "Không được bắt C++ khi chỉ có C")
        assertFalse(skillIds2.contains(7), "Không được bắt C# khi chỉ có C")
    }
}
