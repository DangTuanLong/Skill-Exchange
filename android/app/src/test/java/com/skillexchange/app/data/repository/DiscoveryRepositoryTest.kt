package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.discovery.DiscoveryRemoteDataSource
import com.skillexchange.app.domain.model.SkillType
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

class DiscoveryRepositoryTest {

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

    private val sampleDiscoveryJson = """
    {
        "success": true,
        "data": {
            "items": [
                {
                    "user_id": "user-disc-1",
                    "full_name": "Lê Thị Khám Phá",
                    "bio": "Thích chia sẻ kiến thức thiết kế",
                    "city": "Đà Nẵng",
                    "avatar_url": "https://example.com/avatar.png",
                    "skills": [
                        {
                            "id": "sk-1",
                            "skill_id": 10,
                            "skill_name": "UI/UX Design",
                            "category_name": "Design",
                            "type": "HAVE",
                            "proficiency_level": 4,
                            "note": "Figma master"
                        },
                        {
                            "id": "sk-2",
                            "skill_id": 20,
                            "skill_name": "Kotlin",
                            "category_name": "Tech",
                            "type": "WANT",
                            "proficiency_level": 2,
                            "note": "Muốn học Android"
                        }
                    ]
                }
            ],
            "nextCursor": "cursor-xyz-123"
        }
    }
    """.trimIndent()

    @Test
    fun searchUsers_Success_ReturnsDiscoverySearchResult() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleDiscoveryJson)
        val repo = DiscoveryRepositoryImpl(DiscoveryRemoteDataSource(client))

        val result = repo.searchUsers(
            query = "Design",
            categoryIds = listOf(1, 2),
            city = "Đà Nẵng",
            minProficiency = 3,
            maxProficiency = 5,
            type = "HAVE",
            limit = 20,
            lastId = null
        )

        assertTrue(result.isSuccess)
        val searchResult = result.getOrNull()
        assertNotNull(searchResult)
        assertEquals(1, searchResult?.users?.size)
        assertEquals("cursor-xyz-123", searchResult?.nextCursor)

        val user = searchResult?.users?.first()
        assertEquals("user-disc-1", user?.userId)
        assertEquals("Lê Thị Khám Phá", user?.fullName)
        assertEquals("Đà Nẵng", user?.city)
        assertEquals(2, user?.skills?.size)

        val skillHave = user?.skills?.first { it.skillId == 10 }
        assertEquals(SkillType.HAVE, skillHave?.type)
        assertEquals(4, skillHave?.proficiencyLevel)

        val skillWant = user?.skills?.first { it.skillId == 20 }
        assertEquals(SkillType.WANT, skillWant?.type)
    }

    @Test
    fun searchUsers_EmptyResult_ReturnsEmptyList() = runTest {
        val emptyJson = """{"success":true,"data":{"items":[],"nextCursor":null}}"""
        val client = createMockClient(HttpStatusCode.OK, emptyJson)
        val repo = DiscoveryRepositoryImpl(DiscoveryRemoteDataSource(client))

        val result = repo.searchUsers(
            query = "NonExistent",
            categoryIds = null,
            city = null,
            minProficiency = null,
            maxProficiency = null,
            type = null,
            limit = 20,
            lastId = null
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.users?.size)
        assertNull(result.getOrNull()?.nextCursor)
    }

    @Test
    fun searchUsers_ApiError_ReturnsFailure() = runTest {
        val errorJson = """{"success":false,"code":"VALIDATION_FAILED","message":"Limit không hợp lệ"}"""
        val client = createMockClient(HttpStatusCode.UnprocessableEntity, errorJson)
        val repo = DiscoveryRepositoryImpl(DiscoveryRemoteDataSource(client))

        val result = repo.searchUsers(
            query = null,
            categoryIds = null,
            city = null,
            minProficiency = null,
            maxProficiency = null,
            type = null,
            limit = 100,
            lastId = null
        )

        assertTrue(result.isFailure)
    }
}
