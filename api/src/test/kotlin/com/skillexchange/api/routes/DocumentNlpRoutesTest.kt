package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.nlp.DocumentRateLimiter
import com.skillexchange.api.services.nlp.SkillDefinition
import com.skillexchange.api.services.nlp.SkillExtractionService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import kotlinx.serialization.json.Json
import java.util.concurrent.Semaphore
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentNlpRoutesTest {

    private val testSkills = listOf(
        SkillDefinition(1, "Android (Kotlin)", 1, "Lập trình"),
        SkillDefinition(2, "Python", 1, "Lập trình"),
        SkillDefinition(3, "Java", 1, "Lập trình"),
        SkillDefinition(4, "JavaScript", 1, "Lập trình"),
        SkillDefinition(5, "C++", 1, "Lập trình"),
        SkillDefinition(8, "Tiếng Anh", 2, "Ngoại ngữ"),
        SkillDefinition(9, "Guitar", 3, "Âm nhạc"),
        SkillDefinition(10, "Adobe Photoshop", 4, "Thiết kế")
    )

    private val skillExtractionService = SkillExtractionService(skillProvider = { testSkills })

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
    fun testExtractSkillsDocument_validTxtFile_returnsSkills() = testApp { client ->
        val fileContent = "Xin chào, tôi có kinh nghiệm lập trình Android với Kotlin và sử dụng Python cho xử lý dữ liệu."
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileContent.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                            append(HttpHeaders.ContentDisposition, "filename=\"resume.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertTrue(body.data.any { it.skillName == "Android (Kotlin)" })
        assertTrue(body.data.any { it.skillName == "Python" })
    }

    @Test
    fun testExtractSkillsDocument_validMdFile_returnsSkills() = testApp { client ->
        val fileContent = """
            # Kỹ năng cá nhân
            - Lập trình: Java, Python, C++
            - Ngoại ngữ: Tiếng Anh giao tiếp tốt
        """.trimIndent()

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileContent.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/markdown; charset=utf-8")
                            append(HttpHeaders.ContentDisposition, "filename=\"skills.md\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertTrue(body.data.any { it.skillName == "Java" })
        assertTrue(body.data.any { it.skillName == "Python" })
        assertTrue(body.data.any { it.skillName == "Tiếng Anh" })
    }

    @Test
    fun testExtractSkillsDocument_fileTooLarge_returns413PayloadTooLarge() = testApp { client ->
        // Tạo file > 2MB (2 * 1024 * 1024 + 1024 bytes)
        val oversizedBytes = ByteArray(2 * 1024 * 1024 + 1024) { 'a'.code.toByte() }

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", oversizedBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"big.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, err.code)
        assertTrue(err.message.contains("giới hạn") || err.message.contains("2MB"))
    }

    @Test
    fun testExtractSkillsDocument_textTooLong_returns422TextTooLong() = testApp { client ->
        // Tạo chuỗi văn bản dài 10.050 ký tự (< 2MB nhưng > 10.000 ký tự)
        val longText = "a".repeat(10_050)

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", longText.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"long.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.TEXT_TOO_LONG, err.code)
        assertTrue(err.message.contains("10.000"))
        assertFalse(err.success)
    }

    @Test
    fun testExtractSkillsDocument_invalidUtf8_returns422InvalidEncoding() = testApp { client ->
        // Chuỗi byte không hợp lệ trong UTF-8 (ví dụ 0xC0 hoặc 0xFF)
        val brokenBytes = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0xC0.toByte(), 0x20, 0xFF.toByte())

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", brokenBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"broken.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.INVALID_ENCODING, err.code)
        assertTrue(err.message.contains("UTF-8"))
    }

    @Test
    fun testExtractSkillsDocument_emptyFile_returns422EmptyFile() = testApp { client ->
        val emptyBytes = ByteArray(0)

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", emptyBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"empty.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.EMPTY_FILE, err.code)
    }

    @Test
    fun testExtractSkillsDocument_zipExtension_returns422UnsupportedFileType() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "fake content".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "application/zip")
                            append(HttpHeaders.ContentDisposition, "filename=\"archive.zip\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNSUPPORTED_FILE_TYPE, err.code)
        assertTrue(err.message.contains("ZIP"))
    }

    @Test
    fun testExtractSkillsDocument_zipMagicBytes_returns422UnsupportedFileType() = testApp { client ->
        // Tệp có tên là document.txt nhưng nội dung bắt đầu bằng magic header của ZIP (PK\x03\x04)
        val disguisedZipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00)

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", disguisedZipBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"disguised.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNSUPPORTED_FILE_TYPE, err.code)
        assertTrue(err.message.contains("ZIP"))
    }

    @Test
    fun testExtractSkillsDocument_noJwt_returns401Unauthorized() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "Kotlin content".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testExtractSkillsDocument_rateLimiting_returns429RateLimited() = testApp(
        customDocumentRateLimiter = DocumentRateLimiter(maxPerMinute = 2)
    ) { client ->
        val token = createTestJwt("user-rate-test-distinct")
        val makeReq = suspend {
            client.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", "Tôi học Python".toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                            })
                        }
                    )
                )
            }
        }

        assertEquals(HttpStatusCode.OK, makeReq().status)
        assertEquals(HttpStatusCode.OK, makeReq().status)
        val res3 = makeReq()
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)
        val err = Json.decodeFromString<ApiError>(res3.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, err.code)
    }

    @Test
    fun testExtractSkillsDocument_concurrencyLimit_returns503ServerBusy() = testApp(
        customConcurrencyLimiter = Semaphore(0)
    ) { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("concurrency-test-user")}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "Nội dung tài liệu".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.SERVER_BUSY, err.code)
    }

    @Test
    fun testExtractSkillsDocument_benchmarkLongText_measuresRealTime() = testApp { client ->
        // Tạo văn bản thực tế gần 10.000 ký tự (chứa nhiều đoạn văn và các từ khóa kỹ năng)
        val paragraph = """
            Trong quá trình công tác, tôi đã phát triển nhiều dự án ứng dụng di động Android bằng ngôn ngữ Kotlin.
            Hệ thống backend được xây dựng trên nền tảng Python và Java với khả năng xử lý dữ liệu lớn.
            Tôi có thể giao tiếp và đọc tài liệu chuyên ngành bằng Tiếng Anh. Ngoài ra, tôi có sở thích chơi Guitar và thiết kế hình ảnh với Adobe Photoshop.
        """.trimIndent()

        // Lặp lại để đạt ~9.500 ký tự
        val sb = StringBuilder()
        while (sb.length + paragraph.length < 9_800) {
            sb.append(paragraph).append("\n\n")
        }
        val realLongText = sb.toString()
        assertTrue(realLongText.length in 8_000..9_999)

        var responseStatus: HttpStatusCode? = null
        var responseBody: ExtractSkillsResponse? = null

        val elapsedMs = measureTimeMillis {
            val response = client.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", realLongText.toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                                append(HttpHeaders.ContentDisposition, "filename=\"cv_full.txt\"")
                            })
                        }
                    )
                )
            }
            responseStatus = response.status
            responseBody = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        }

        assertEquals(HttpStatusCode.OK, responseStatus)
        assertTrue(responseBody!!.success)
        assertTrue(responseBody!!.data.isNotEmpty())

        println("BENCHMARK_RESULT: Thời gian xử lý văn bản ${realLongText.length} ký tự là: ${elapsedMs}ms")
        // Đảm bảo thời gian xử lý < 2000ms (thực tế in-process thường < 100ms)
        assertTrue(elapsedMs < 2000, "Thời gian xử lý $elapsedMs ms vượt quá ngưỡng chấp nhận 2000ms")
    }
}
