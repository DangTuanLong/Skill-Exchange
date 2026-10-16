package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.graph.GraphUserNodeDto
import com.skillexchange.api.models.graph.SuggestPathResponse
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.graph.EdgeCandidate
import com.skillexchange.api.services.graph.GraphRateLimiter
import com.skillexchange.api.services.graph.SkillGraphService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphRoutesTest {

    private val testNodes = listOf(
        GraphUserNodeDto("caller-user", "Caller User"),
        GraphUserNodeDto("user-b", "User B"),
        GraphUserNodeDto("user-c", "User C")
    )

    private val testEdges = listOf(
        EdgeCandidate("caller-user", "user-b", 1, "Figma", 4, 2),
        EdgeCandidate("user-b", "user-c", 2, "Guitar", 3, 1),
        EdgeCandidate("user-c", "caller-user", 3, "Tiếng Anh giao tiếp", 5, 2)
    )

    private val graphService = SkillGraphService(
        memoryGraphDataProvider = { Pair(testNodes, testEdges) }
    )

    private val rateLimiter = GraphRateLimiter(maxPerMinute = 5)

    private fun createTestJwt(userId: String = "caller-user"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun testGraphApp(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
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
                graphRoutes(graphService, rateLimiter)
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
    fun `GET suggest-path returns 401 when no token provided`() = testGraphApp { client ->
        val response = client.get("/api/graph/suggest-path")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET suggest-path returns 422 when depth is out of range`() = testGraphApp { client ->
        val token = createTestJwt("caller-user")
        val response = client.get("/api/graph/suggest-path?depth=5") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, body.code)
        assertTrue(body.fieldErrors?.containsKey("depth") == true)
    }

    @Test
    fun `GET suggest-path returns 422 when limit is out of range`() = testGraphApp { client ->
        val token = createTestJwt("caller-user")
        val response = client.get("/api/graph/suggest-path?limit=0") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, body.code)
        assertTrue(body.fieldErrors?.containsKey("limit") == true)
    }

    @Test
    fun `GET suggest-path returns 200 with loops separated into loops2 and loops3Plus`() = testGraphApp { client ->
        val token = createTestJwt("caller-user")
        val response = client.get("/api/graph/suggest-path?depth=3&limit=5") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<SuggestPathResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertEquals("caller-user", body.callerId)
        assertEquals(0, body.loops2.size)
        assertEquals(1, body.loops3Plus.size)

        val loop = body.loops3Plus.first()
        assertEquals(3, loop.cycleLength)
        assertEquals(3, loop.nodes.size)
        assertEquals(3, loop.edges.size)
        assertEquals("Figma", loop.edges[0].skillName)
    }

    @Test
    fun `GET suggest-path enforces rate limit and returns 429`() = testGraphApp { client ->
        val userId = "rate-limited-user"
        val token = createTestJwt(userId)

        // Thực hiện 5 request đầu (maxPerMinute = 5)
        repeat(5) {
            val res = client.get("/api/graph/suggest-path") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
        }

        // Request thứ 6 phải trả về 429
        val rateLimitedRes = client.get("/api/graph/suggest-path") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, rateLimitedRes.status)
        val body = Json.decodeFromString<ApiError>(rateLimitedRes.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, body.code)
    }
}
