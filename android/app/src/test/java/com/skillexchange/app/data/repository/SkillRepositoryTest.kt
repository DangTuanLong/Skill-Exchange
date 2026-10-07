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
