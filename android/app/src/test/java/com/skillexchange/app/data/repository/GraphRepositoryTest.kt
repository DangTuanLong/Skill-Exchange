package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.graph.GraphRemoteDataSource
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

class GraphRepositoryTest {

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

    private val sampleGraphResponseJson = """
    {
        "success": true,
        "callerId": "user-caller-1",
        "loops2": [
            {
                "loopId": "loop-2w",
                "cycleLength": 2,
                "nodes": [
                    {"userId": "user-caller-1", "fullName": "Tôi", "avatarUrl": null, "city": "Hà Nội"},
                    {"userId": "user-partner-2", "fullName": "Đối tác B", "avatarUrl": null, "city": "Hà Nội"}
                ],
                "edges": [
                    {
                        "fromUserId": "user-caller-1",
                        "toUserId": "user-partner-2",
                        "skillId": 1,
                        "skillName": "Kotlin",
                        "haveLevel": 4,
                        "wantLevel": 2,
                        "alternativeSkills": []
                    },
                    {
                        "fromUserId": "user-partner-2",
                        "toUserId": "user-caller-1",
                        "skillId": 2,
                        "skillName": "Python",
                        "haveLevel": 3,
                        "wantLevel": 2,
                        "alternativeSkills": []
                    }
                ]
            }
        ],
        "loops3Plus": [
            {
                "loopId": "loop-3w",
                "cycleLength": 3,
                "nodes": [
                    {"userId": "user-caller-1", "fullName": "Tôi", "avatarUrl": null, "city": "Hà Nội"},
                    {"userId": "user-partner-2", "fullName": "Đối tác B", "avatarUrl": null, "city": "TP.HCM"},
                    {"userId": "user-partner-3", "fullName": "Đối tác C", "avatarUrl": null, "city": "Đà Nẵng"}
                ],
                "edges": [
                    {
                        "fromUserId": "user-caller-1",
                        "toUserId": "user-partner-2",
                        "skillId": 1,
                        "skillName": "Kotlin",
                        "haveLevel": 4,
                        "wantLevel": 2,
                        "alternativeSkills": [
                            {"skillId": 11, "skillName": "Java", "haveLevel": 4, "wantLevel": 3}
                        ]
                    },
                    {
                        "fromUserId": "user-partner-2",
                        "toUserId": "user-partner-3",
                        "skillId": 2,
                        "skillName": "Guitar",
                        "haveLevel": 3,
                        "wantLevel": 1,
                        "alternativeSkills": []
                    },
                    {
                        "fromUserId": "user-partner-3",
                        "toUserId": "user-caller-1",
                        "skillId": 3,
                        "skillName": "Tiếng Anh",
                        "haveLevel": 5,
                        "wantLevel": 2,
                        "alternativeSkills": []
                    }
                ]
            }
        ],
        "disclaimer": "Chu trình chỉ là gợi ý kết nối"
    }
    """.trimIndent()

    @Test
    fun getSuggestedPaths_Success_ReturnsSuggestPathResult() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleGraphResponseJson)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = GraphRepositoryImpl(GraphRemoteDataSource(client), tokenManager)

        val result = repo.getSuggestedPaths(depth = 3, limit = 10)

        assertTrue(result.isSuccess)
        val pathResult = result.getOrNull()
        assertNotNull(pathResult)
        assertEquals("user-caller-1", pathResult?.callerId)
        assertEquals(1, pathResult?.loops2?.size)
        assertEquals(1, pathResult?.loops3Plus?.size)

        val loop3 = pathResult?.loops3Plus?.first()
        assertEquals(3, loop3?.cycleLength)
        assertEquals(3, loop3?.nodes?.size)
        assertEquals(3, loop3?.edges?.size)
        assertEquals(1, loop3?.edges?.first()?.alternativeSkills?.size)
        assertEquals("Java", loop3?.edges?.first()?.alternativeSkills?.first()?.skillName)
    }

    @Test
    fun getSuggestedPaths_NotLoggedIn_ReturnsFailure() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleGraphResponseJson)
        val tokenManager = TokenManager(null) // no access token
        val repo = GraphRepositoryImpl(GraphRemoteDataSource(client), tokenManager)

        val result = repo.getSuggestedPaths(depth = 3, limit = 10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
