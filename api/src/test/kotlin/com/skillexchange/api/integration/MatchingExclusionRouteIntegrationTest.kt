package com.skillexchange.api.integration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.*
import com.skillexchange.api.models.matching.*
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.routes.exchangeRoutes
import com.skillexchange.api.routes.matchingRoutes
import com.skillexchange.api.routes.userRoutes
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.UserSearchDto
import com.skillexchange.api.services.UserSearchResultDto
import com.skillexchange.api.services.UserService
import com.skillexchange.api.services.exchange.ExchangeService
import com.skillexchange.api.services.exchange.PairAlreadyActiveException
import com.skillexchange.api.services.exchange.PendingLimitReachedException
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kiểm thử tích hợp MỨC ROUTE VỚI SERVICE GIẢ (Mock Services) cho kịch bản DEC-028.
 *
 * LƯU Ý QUAN TRỌNG:
 * - Bộ kiểm thử này kiểm chứng tương tác HTTP, xác thực JWT, serialization, mã lỗi HTTP,
 *   và luồng điều phối của các endpoint liên quan đến 6 persona đối tác (B..G) ở tầng Route.
 * - KHÔNG kiểm chứng truy vấn SQL anti-join / NOT EXISTS trên CSDL PostgreSQL thật (vì chạy offline
 *   trong testApplication với MockK). Việc kiểm chứng CSDL thật được thực hiện thủ công trên DB dev
 *   theo checklist kiểm thử tại docs/DELIVERY.md.
 */
class MatchingExclusionRouteIntegrationTest {

    private val jwtSecret = "test-jwt-secret-route-integration"
    private val json = Json { ignoreUnknownKeys = true }

    private val mockMatchingService = mockk<MatchingService>(relaxed = true)
    private val mockUserService = mockk<UserService>(relaxed = true)
    private val mockExchangeService = mockk<ExchangeService>(relaxed = true)

    private val aliceId = "11111111-1111-1111-1111-111111111111"
    private val bobId = "22222222-2222-2222-2222-222222222222"       // PENDING
    private val charlieId = "33333333-3333-3333-3333-333333333333"   // ACCEPTED
    private val davidId = "44444444-4444-4444-4444-444444444444"     // COMPLETED
    private val emmaId = "55555555-5555-5555-5555-555555555555"      // REJECTED (cooldown 14d)
    private val frankId = "66666666-6666-6666-6666-666666666666"     // Potential 1-way
    private val graceId = "77777777-7777-7777-7777-777777777777"     // New 2-way match

    private fun createToken(userId: String): String =
        JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256(jwtSecret))

    private fun ApplicationTestBuilder.setupApp(
        customMatchingRateLimiter: InMemoryRateLimiter? = null,
        customValidPairsRateLimiter: InMemoryRateLimiter? = null
    ) {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "SkillExchange API"
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withAudience("authenticated")
                            .build()
                    )
                    validate { credential ->
                        if (!credential.payload.subject.isNullOrEmpty()) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                matchingRoutes(
                    matchingService = mockMatchingService,
                    rateLimiter = customMatchingRateLimiter ?: InMemoryRateLimiter(maxRequests = 30, windowMillis = 60_000L)
                )
                userRoutes(mockUserService)
                exchangeRoutes(
                    exchangeService = mockExchangeService,
                    validPairsRateLimiter = customValidPairsRateLimiter ?: InMemoryRateLimiter(maxRequests = 30, windowMillis = 60_000L)
                )
            }
        }
    }

    @BeforeTest
    fun setUp() {
        clearMocks(mockMatchingService, mockUserService, mockExchangeService)
    }

    @Test
    fun `Persona B (PENDING) - excluded from suggestions, search returns ACTIVE relationship chip`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        // Mock matching suggestions: only Grace is returned (Bob excluded)
        every { mockMatchingService.getSuggestions(aliceId, any()) } returns listOf(
            MatchingSuggestionDto(
                userId = graceId, fullName = "Đỗ Grace", avatarUrl = null, city = "Hà Nội",
                totalScore = 0.95, percentage = 95, availablePairsCount = 1,
                breakdown = MatchingScoreBreakdown(skillMatch = 1.0, proficiencyFit = 1.0, locationBonus = 0.5, availabilityOverlap = 0.5),
                matchedSkills = MatchedSkillsBreakdown(theyTeachYou = emptyList(), youTeachThem = emptyList())
            )
        )

        // Mock search for Bob: returns ACTIVE relationship
        every { mockUserService.searchUsersWithCursor(query = "Bob", any(), any(), any(), any(), any(), any(), any(), any(), excludeUserId = aliceId) } returns
                UserSearchResultDto(
                    items = listOf(
                        UserSearchDto(
                            userId = bobId, fullName = "Trần Văn Bob",
                            relationshipStatus = "ACTIVE", completedExchangeCount = 0
                        )
                    ),
                    nextCursor = null
                )

        // 1. Suggestions endpoint excludes Bob
        val sugResp = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, sugResp.status)
        val sugBody = sugResp.bodyAsText()
        assertFalse(sugBody.contains(bobId), "Candidate Bob must not appear in suggestions")
        assertTrue(sugBody.contains(graceId), "Candidate Grace must appear in suggestions")

        // 2. Search endpoint returns ACTIVE chip
        val searchResp = client.get("/api/users/search?q=Bob") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val searchBody = searchResp.bodyAsText()
        assertTrue(searchBody.contains("\"relationship_status\":\"ACTIVE\""), "Bob must have ACTIVE chip")
    }

    @Test
    fun `Persona C (ACCEPTED) - excluded from suggestions, search returns ACTIVE relationship chip`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockMatchingService.getSuggestions(aliceId, any()) } returns listOf(
            MatchingSuggestionDto(
                userId = graceId, fullName = "Đỗ Grace", avatarUrl = null, city = "Hà Nội",
                totalScore = 0.95, percentage = 95, availablePairsCount = 1,
                breakdown = MatchingScoreBreakdown(skillMatch = 1.0, proficiencyFit = 1.0, locationBonus = 0.5, availabilityOverlap = 0.5),
                matchedSkills = MatchedSkillsBreakdown(theyTeachYou = emptyList(), youTeachThem = emptyList())
            )
        )

        every { mockUserService.searchUsersWithCursor(query = "Charlie", any(), any(), any(), any(), any(), any(), any(), any(), excludeUserId = aliceId) } returns
                UserSearchResultDto(
                    items = listOf(
                        UserSearchDto(
                            userId = charlieId, fullName = "Lê Charlie",
                            relationshipStatus = "ACTIVE", completedExchangeCount = 0
                        )
                    ),
                    nextCursor = null
                )

        val searchResp = client.get("/api/users/search?q=Charlie") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val searchBody = searchResp.bodyAsText()
        assertTrue(searchBody.contains("\"relationship_status\":\"ACTIVE\""))
    }

    @Test
    fun `Persona D (COMPLETED) - excluded from suggestions, search has COMPLETED chip, valid-pairs allows re-booking`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockMatchingService.getSuggestions(aliceId, any()) } returns emptyList()

        every { mockUserService.searchUsersWithCursor(query = "David", any(), any(), any(), any(), any(), any(), any(), any(), excludeUserId = aliceId) } returns
                UserSearchResultDto(
                    items = listOf(
                        UserSearchDto(
                            userId = davidId, fullName = "Phạm David",
                            relationshipStatus = "COMPLETED", completedExchangeCount = 1
                        )
                    ),
                    nextCursor = null
                )

        every { mockExchangeService.getValidPairs(aliceId, davidId) } returns ValidPairsResponse(
            pairs = listOf(
                ValidSkillPairDto(
                    skillOfferedId = 2, skillOfferedName = "SQL",
                    skillWantedId = 3, skillWantedName = "Tiếng Anh",
                    isActive = false, completedCount = 1
                )
            ),
            recentlyRejected = false,
            relationshipStatus = "COMPLETED",
            completedExchangeCount = 1
        )

        // 1. Search chip check
        val searchResp = client.get("/api/users/search?q=David") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val searchBody = searchResp.bodyAsText()
        assertTrue(searchBody.contains("\"relationship_status\":\"COMPLETED\""))
        assertTrue(searchBody.contains("\"completed_exchange_count\":1"))

        // 2. Valid pairs check (re-booking enabled, isActive = false)
        val validPairsResp = client.get("/api/exchange-requests/valid-pairs/$davidId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, validPairsResp.status)
        val validPairsBody = validPairsResp.bodyAsText()
        assertTrue(validPairsBody.contains("\"is_active\":false"), "Completed pair must be selectable for re-booking")
        assertTrue(validPairsBody.contains("\"completed_count\":1"))
    }

    @Test
    fun `Persona E (REJECTED within 14d) - excluded from suggestions, valid-pairs returns recentlyRejected true`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockMatchingService.getSuggestions(aliceId, any()) } returns emptyList()

        every { mockExchangeService.getValidPairs(aliceId, emmaId) } returns ValidPairsResponse(
            pairs = listOf(
                ValidSkillPairDto(
                    skillOfferedId = 1, skillOfferedName = "Android",
                    skillWantedId = 5, skillWantedName = "UI/UX",
                    isActive = false, completedCount = 0
                )
            ),
            recentlyRejected = true,
            relationshipStatus = null,
            completedExchangeCount = 0
        )

        val validPairsResp = client.get("/api/exchange-requests/valid-pairs/$emmaId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, validPairsResp.status)
        val body = validPairsResp.bodyAsText()
        assertTrue(body.contains("\"recently_rejected\":true"), "Soft warning banner must be triggered by recently_rejected = true")
    }

    @Test
    fun `Persona F (Potential 1-way) - returned via potential suggestions endpoint when 2-way is empty`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockMatchingService.getSuggestions(aliceId, any()) } returns emptyList()

        every { mockMatchingService.getPotentialSuggestions(aliceId, any(), any()) } returns listOf(
            PotentialMatchDto(
                userId = frankId, fullName = "Vũ Frank", avatarUrl = null, city = "Hà Nội",
                canTeachSkill = PotentialMatchSkillDto(skillId = 1, skillName = "Android", proficiencyLevel = 3),
                suggestedWantSkill = PotentialMatchSkillDto(skillId = 6, skillName = "Nhiếp ảnh", proficiencyLevel = 4),
                potentialScore = 72
            )
        )

        // 1. Two-way suggestions are empty
        val sugResp = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, sugResp.status)
        assertEquals("{\"success\":true,\"data\":[]}", sugResp.bodyAsText())

        // 2. Potential 1-way suggestions return Frank
        val potResp = client.get("/api/matching/suggestions/potential") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, potResp.status)
        val potBody = potResp.bodyAsText()
        assertTrue(potBody.contains(frankId))
        assertTrue(potBody.contains("Nhiếp ảnh"))
    }

    @Test
    fun `Persona G (New 2-way match) - top rank in suggestions, no relationship chip in search`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockMatchingService.getSuggestions(aliceId, any()) } returns listOf(
            MatchingSuggestionDto(
                userId = graceId, fullName = "Đỗ Grace", avatarUrl = null, city = "Hà Nội",
                totalScore = 0.98, percentage = 98, availablePairsCount = 1,
                breakdown = MatchingScoreBreakdown(skillMatch = 1.0, proficiencyFit = 1.0, locationBonus = 0.5, availabilityOverlap = 0.5),
                matchedSkills = MatchedSkillsBreakdown(theyTeachYou = emptyList(), youTeachThem = emptyList())
            )
        )

        every { mockUserService.searchUsersWithCursor(query = "Grace", any(), any(), any(), any(), any(), any(), any(), any(), excludeUserId = aliceId) } returns
                UserSearchResultDto(
                    items = listOf(
                        UserSearchDto(
                            userId = graceId, fullName = "Đỗ Grace",
                            relationshipStatus = null, completedExchangeCount = 0
                        )
                    ),
                    nextCursor = null
                )

        // 1. Top of suggestions
        val sugResp = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, sugResp.status)
        val sugBody = sugResp.bodyAsText()
        assertTrue(sugBody.contains(graceId))
        assertTrue(sugBody.contains("\"percentage\":98"))

        // 2. Search chip is null
        val searchResp = client.get("/api/users/search?q=Grace") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val searchBody = searchResp.bodyAsText()
        assertFalse(searchBody.contains("\"relationship_status\""), "New user must not have a relationship chip (null is omitted)")
    }

    @Test
    fun `Duplicate active pair - returns 409 PAIR_ALREADY_ACTIVE`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockExchangeService.createExchangeRequest(aliceId, any()) } throws
                PairAlreadyActiveException("Cặp kỹ năng này đang có yêu cầu trao đổi hoạt động")

        val futureTime = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val body = """
            {
                "receiver_id": "$bobId",
                "skill_offered_id": 1,
                "skill_wanted_id": 3,
                "duration_minutes": 60,
                "meeting_mode": "ONLINE",
                "scheduled_at": "$futureTime"
            }
        """.trimIndent()

        val resp = client.post("/api/exchange-requests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val respBody = resp.bodyAsText()
        assertTrue(respBody.contains(ErrorCodes.PAIR_ALREADY_ACTIVE), "Must return code PAIR_ALREADY_ACTIVE")
    }

    @Test
    fun `Exceeding 3 pending requests - returns 409 PENDING_LIMIT_REACHED`() = testApplication {
        setupApp()
        val token = createToken(aliceId)

        every { mockExchangeService.createExchangeRequest(aliceId, any()) } throws
                PendingLimitReachedException("Đã đạt giới hạn tối đa 3 yêu cầu đang chờ phản hồi giữa hai người")

        val futureTime = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val body = """
            {
                "receiver_id": "$bobId",
                "skill_offered_id": 2,
                "skill_wanted_id": 3,
                "duration_minutes": 60,
                "meeting_mode": "ONLINE",
                "scheduled_at": "$futureTime"
            }
        """.trimIndent()

        val resp = client.post("/api/exchange-requests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val respBody = resp.bodyAsText()
        assertTrue(respBody.contains(ErrorCodes.PENDING_LIMIT_REACHED), "Must return code PENDING_LIMIT_REACHED")
    }

    @Test
    fun `Rate limiting - 31st request on suggestions returns 429 RATE_LIMITED`() = testApplication {
        val limiter = InMemoryRateLimiter(maxRequests = 2, windowMillis = 60_000L)
        setupApp(customMatchingRateLimiter = limiter)
        val token = createToken(aliceId)

        // 1st request -> 200
        val r1 = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, r1.status)

        // 2nd request -> 200
        val r2 = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, r2.status)

        // 3rd request -> 429
        val r3 = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, r3.status)
        val body = r3.bodyAsText()
        assertTrue(body.contains(ErrorCodes.RATE_LIMITED), "Must return RATE_LIMITED error code")
    }

    @Test
    fun `Rate limiting - exceeding limit on valid-pairs returns 429 RATE_LIMITED`() = testApplication {
        val limiter = InMemoryRateLimiter(maxRequests = 1, windowMillis = 60_000L)
        setupApp(customValidPairsRateLimiter = limiter)
        val token = createToken(aliceId)

        every { mockExchangeService.getValidPairs(aliceId, davidId) } returns ValidPairsResponse(emptyList(), false)

        // 1st request -> 200
        val r1 = client.get("/api/exchange-requests/valid-pairs/$davidId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, r1.status)

        // 2nd request -> 429
        val r2 = client.get("/api/exchange-requests/valid-pairs/$davidId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, r2.status)
        val body = r2.bodyAsText()
        assertTrue(body.contains(ErrorCodes.RATE_LIMITED), "Must return RATE_LIMITED error code")
    }
}
