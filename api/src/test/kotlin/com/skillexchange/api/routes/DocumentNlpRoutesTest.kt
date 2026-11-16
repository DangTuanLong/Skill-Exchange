package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.models.nlp.ExtractedSkillDto
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.nlp.DocumentRateLimiter
import com.skillexchange.api.services.nlp.GeminiExtractionResult
import com.skillexchange.api.services.nlp.GeminiSkillExtractionService
import com.skillexchange.api.services.nlp.SkillDefinition
import com.skillexchange.api.services.nlp.SkillExtractionService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentNlpRoutesTest {

    private val testSkills = listOf(
        SkillDefinition(1, "Android (Kotlin)", 1, "Lập trình"),
        SkillDefinition(2, "Python", 1, "Lập trình"),
        SkillDefinition(3, "Java", 1, "Lập trình"),
        SkillDefinition(4, "JavaScript", 1, "Lập trình")
    )

    private val skillExtractionService = SkillExtractionService(skillProvider = { testSkills })
    private val mockGeminiService = mockk<GeminiSkillExtractionService>()

    private fun createTestJwt(userId: String = "test-doc-user-1"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun testApp(
        customDocumentRateLimiter: DocumentRateLimiter = DocumentRateLimiter(maxPerMinute = 10),
        customConcurrencyLimiter: Semaphore = Semaphore(3),
        block: suspend (io.ktor.client.HttpClient) -> Unit
    ) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(Algorithm.HMAC256("test-secret"))
                            .withAudience("authenticated")
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.subject != null) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                nlpRoutes(
                    skillExtractionService = skillExtractionService,
                    geminiService = mockGeminiService,
                    documentRateLimiter = customDocumentRateLimiter,
                    documentConcurrencyLimiter = customConcurrencyLimiter
                )
            }
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        block(client)
    }

    @Test
    fun testExtractSkillsDocument_validTxtFile_returns200AndExtractsSkills() = testApp { client ->
        coEvery { mockGeminiService.extractSkillsFromMultimodal(any(), any(), any()) } returns GeminiExtractionResult(
            profileSummary = "Kỹ sư Android",
            skills = listOf(
                ExtractedSkillDto(skillId = 1, skillName = "Android (Kotlin)", confidence = 0.95, matchType = "AI_GEMINI", matchedTerm = "Android (Kotlin)"),
                ExtractedSkillDto(skillId = 3, skillName = "Java", confidence = 0.90, matchType = "AI_GEMINI", matchedTerm = "Java")
            )
        )

        val fileContent = "Kỹ sư phần mềm thành thạo lập trình Android (Kotlin) và Java."
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileContent.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"my_cv.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertEquals("Kỹ sư Android", body.profileSummary)
        val skillNames = body.data.map { it.skillName }
        assertTrue(skillNames.contains("Android (Kotlin)"))
        assertTrue(skillNames.contains("Java"))
    }

    @Test
    fun testExtractSkillsDocument_validJsonText_returns200() = testApp { client ->
        coEvery { mockGeminiService.extractSkillsFromText(any()) } returns GeminiExtractionResult(
            profileSummary = "Python Developer",
            skills = listOf(
                ExtractedSkillDto(skillId = 2, skillName = "Python", confidence = 0.95, matchType = "AI_GEMINI", matchedTerm = "Python")
            )
        )

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Lập trình Python chuyên nghiệp","fileName":"bio.txt"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertEquals(1, body.data.size)
        assertEquals("Python", body.data[0].skillName)
    }

    @Test
    fun testExtractSkillsDocument_fileTooLarge_returns413PayloadTooLarge() = testApp { client ->
        val chunk = "0123456789ABCDEF".toByteArray(Charsets.UTF_8) // 16 bytes
        val totalChunks = (10 * 1024 * 1024 / 16) + 10 // > 10MB
        val largeBytes = ByteArrayOutputStream().apply {
            repeat(totalChunks) { write(chunk) }
        }.toByteArray()

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", largeBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"large_file.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        val error = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, error.code)
    }

    @Test
    fun testExtractSkillsDocument_emptyFile_returns422EmptyFile() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", ByteArray(0), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"empty.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val error = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.EMPTY_FILE, error.code)
    }

    @Test
    fun testExtractSkillsDocument_noFilePart_returns422EmptyFile() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("description", "no file attached")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val error = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.EMPTY_FILE, error.code)
    }

    @Test
    fun testExtractSkillsDocument_missingAuthToken_returns401() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "content".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"cv.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testExtractSkillsDocument_rateLimiting_returns429TooManyRequests() = testApp(
        customDocumentRateLimiter = DocumentRateLimiter(maxPerMinute = 2)
    ) { client ->
        coEvery { mockGeminiService.extractSkillsFromMultimodal(any(), any(), any()) } returns GeminiExtractionResult(
            profileSummary = null,
            skills = emptyList()
        )

        val token = createTestJwt("rate-limit-user")
        val makeRequest = suspend {
            client.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", "Kotlin Developer".toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=\"cv.txt\"")
                            })
                        }
                    )
                )
            }
        }

        val res1 = makeRequest()
        assertEquals(HttpStatusCode.OK, res1.status)
        val res2 = makeRequest()
        assertEquals(HttpStatusCode.OK, res2.status)
        val res3 = makeRequest()
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)
        val error = Json.decodeFromString<ApiError>(res3.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, error.code)
    }

    @Test
    fun testExtractSkillsDocument_concurrencyLimit_returns503ServerBusy() = testApp(
        customConcurrencyLimiter = Semaphore(1)
    ) { client ->
        coEvery { mockGeminiService.extractSkillsFromMultimodal(any(), any(), any()) } returns GeminiExtractionResult(
            profileSummary = null,
            skills = emptyList()
        )

        val token = createTestJwt("concurrency-user")
        val semaphore = Semaphore(0)

        // Consume the permit manually to simulate busy server
        val busySem = Semaphore(1)
        busySem.acquire()

        val makeRequestWithBusyLimiter = testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                install(Authentication) {
                    jwt("auth-jwt") {
                        verifier(JWT.require(Algorithm.HMAC256("test-secret")).withAudience("authenticated").build())
                        validate { credential -> if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null }
                    }
                }
                routing {
                    nlpRoutes(
                        skillExtractionService = skillExtractionService,
                        geminiService = mockGeminiService,
                        documentConcurrencyLimiter = Semaphore(0) // 0 permits available
                    )
                }
            }

            val busyClient = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val res = busyClient.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", "content".toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=\"cv.txt\"")
                            })
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
            val error = Json.decodeFromString<ApiError>(res.bodyAsText())
            assertEquals(ErrorCodes.SERVER_BUSY, error.code)
        }
    }
}
