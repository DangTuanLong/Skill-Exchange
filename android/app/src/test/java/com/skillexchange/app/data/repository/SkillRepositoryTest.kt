package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.skill.SkillRemoteDataSource
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRepositoryTest {

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
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

    @Test
    fun getCategories_Success_ReturnsCategories() = runTest {
        val json = """
        {
            "success": true,
            "data": [
                {
                    "id": 1,
                    "name": "Công nghệ",
                    "icon": "code",
                    "skills": [
                        {"id": 10, "category_id": 1, "name": "Kotlin"},
                        {"id": 11, "category_id": 1, "name": "Android"}
                    ]
                }
            ]
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null)
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.getCategories()

        assertTrue(result.isSuccess)
        val categories = result.getOrNull()
        assertEquals(1, categories?.size)
        assertEquals("Công nghệ", categories?.first()?.name)
        assertEquals(2, categories?.first()?.skills?.size)
    }

    @Test
    fun getUserSkills_Success_ReturnsUserSkills() = runTest {
        val json = """
        {
            "success": true,
            "data": [
                {
                    "id": "us-1",
                    "skill_id": 10,
                    "skill_name": "Kotlin",
                    "category_name": "Công nghệ",
                    "type": "HAVE",
                    "proficiency_level": 4,
                    "note": "Senior"
                }
            ]
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null).apply { saveAccessToken("token-123") }
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.getUserSkills("user-123")

        assertTrue(result.isSuccess)
        val skills = result.getOrNull()
        assertEquals(1, skills?.size)
        assertEquals("Kotlin", skills?.first()?.skillName)
    }

    @Test
    fun addUserSkill_Success_ReturnsUserSkill() = runTest {
        val json = """
        {
            "success": true,
            "data": {
                "id": "us-2",
                "skill_id": 11,
                "skill_name": "Android",
                "category_name": "Công nghệ",
                "type": "HAVE",
                "proficiency_level": 3
            }
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null).apply { saveAccessToken("token-123") }
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.addUserSkill(11, "HAVE", 3, null)

        assertTrue(result.isSuccess)
        assertEquals("Android", result.getOrNull()?.skillName)
    }

    @Test
    fun removeUserSkill_Success_ReturnsTrue() = runTest {
        // Matches exact response payload from DELETE /api/skills/user/{id}
        val json = """{"success":true,"data":"Đã xóa"}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test-token") }
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.removeUserSkill("skill-uuid-123")

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun removeUserSkill_ServerError_ReturnsFailure() = runTest {
        val json = """{"success":false,"message":"Không tìm thấy","code":404}"""
        val client = createMockClient(HttpStatusCode.NotFound, json)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test-token") }
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.removeUserSkill("non-existent-uuid")

        assertTrue(result.isFailure)
    }

    @Test
    fun removeUserSkill_WithoutToken_ReturnsFailure() = runTest {
        val json = """{"success":true,"data":"Đã xóa"}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null) // no token saved
        val repository = SkillRepositoryImpl(SkillRemoteDataSource(client), tokenManager)

        val result = repository.removeUserSkill("skill-uuid-123")

        assertTrue(result.isFailure)
        assertEquals("Chưa đăng nhập", result.exceptionOrNull()?.message)
    }
}
