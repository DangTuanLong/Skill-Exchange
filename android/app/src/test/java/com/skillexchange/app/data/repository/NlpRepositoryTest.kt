package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.nlp.NlpRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NlpRepositoryTest {

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    private val sampleSkillsResponseJson = """
    {
        "success": true,
        "data": [
            {
                "skillId": 1,
                "skillName": "Kotlin",
                "categoryId": 1,
                "categoryName": "Lập trình & Công nghệ",
                "confidence": 1.0,
                "matchType": "EXACT",
                "matchedTerm": "Kotlin"
            },
            {
                "skillId": 2,
                "skillName": "Figma",
                "categoryId": 2,
                "categoryName": "Thiết kế & Đồ họa",
                "confidence": 0.9,
                "matchType": "ALIAS",
                "matchedTerm": "UI UX"
            }
        ]
    }
    """.trimIndent()

    @Test
    fun extractSkills_Success_ReturnsSkillList() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleSkillsResponseJson)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = NlpRepositoryImpl(NlpRemoteDataSource(client), tokenManager)

        val result = repo.extractSkills("Tôi biết lập trình Kotlin và thiết kế UI UX")

        assertTrue(result.isSuccess)
        val skills = result.getOrNull()
        assertNotNull(skills)
        assertEquals(2, skills?.size)
        assertEquals("Kotlin", skills?.first()?.skillName)
        assertEquals(1.0, skills?.first()?.confidence ?: 0.0, 0.01)
        assertEquals("Figma", skills?.last()?.skillName)
    }

    @Test
    fun extractSkills_NotLoggedIn_ReturnsFailure() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleSkillsResponseJson)
        val tokenManager = TokenManager(null) // no token
        val repo = NlpRepositoryImpl(NlpRemoteDataSource(client), tokenManager)

        val result = repo.extractSkills("Bio text")

        assertTrue(result.isFailure)
    }

    @Test
    fun extractSkillsFromDocument_Success_ReturnsSkillList() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleSkillsResponseJson)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = NlpRepositoryImpl(NlpRemoteDataSource(client), tokenManager)

        val fileBytes = "CV: Kinh nghiệm làm việc với Kotlin".toByteArray(Charsets.UTF_8)
        val result = repo.extractSkillsFromDocument(fileBytes, "cv.txt")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun extractSkillsFromDocument_ErrorResponse_ParsesErrorMessage() = runTest {
        val errorJson = """{"success":false,"status":422,"code":"TEXT_TOO_LONG","message":"Văn bản trong tài liệu vượt quá 10.000 ký tự"}"""
        val client = createMockClient(HttpStatusCode.UnprocessableEntity, errorJson)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = NlpRepositoryImpl(NlpRemoteDataSource(client), tokenManager)

        val fileBytes = ByteArray(100)
        val result = repo.extractSkillsFromDocument(fileBytes, "cv.txt")

        assertTrue(result.isFailure)
        assertEquals("Văn bản trong tài liệu vượt quá 10.000 ký tự", result.exceptionOrNull()?.message)
    }
}
