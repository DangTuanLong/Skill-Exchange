package com.skillexchange.api.integration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.auth.*
import com.skillexchange.api.models.exchange.*
import com.skillexchange.api.models.matching.*
import com.skillexchange.api.models.profile.*
import com.skillexchange.api.models.rating.*
import com.skillexchange.api.models.skill.*
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.routes.*
import com.skillexchange.api.services.*
import com.skillexchange.api.services.auth.AuthRateLimiter
import com.skillexchange.api.services.auth.IFirebaseTokenService
import com.skillexchange.api.services.exchange.ExchangeConflictException
import com.skillexchange.api.services.exchange.ExchangeForbiddenException
import com.skillexchange.api.services.exchange.ExchangeService
import com.skillexchange.api.services.matching.MatchingService
import com.skillexchange.api.services.rating.RatingConflictException
import com.skillexchange.api.services.rating.RatingForbiddenException
import com.skillexchange.api.services.rating.RatingNotFoundException
import com.skillexchange.api.services.rating.RatingService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.*

class FullUserFlowIntegrationTest {

    private val jwtSecret = "test-jwt-secret-full-flow"

    private fun createToken(userId: String): String =
        JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256(jwtSecret))

    private fun ApplicationTestBuilder.setupIntegratedApp(
        mockAuthService: AuthService,
        mockFirebaseTokenService: IFirebaseTokenService,
        mockAuthRateLimiter: AuthRateLimiter,
        mockProfileService: ProfileService,
        mockAvatarStorageService: IAvatarStorageService,
        mockProfileRateLimiter: InMemoryRateLimiter,
        mockSkillService: SkillService,
        mockUserService: UserService,
        mockMatchingService: MatchingService,
        mockExchangeService: ExchangeService,
        mockRatingService: RatingService
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
                authRoutes(mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter)
                profileRoutes(mockProfileService, mockAvatarStorageService, mockProfileRateLimiter)
                skillRoutes(mockSkillService)
                userRoutes(mockUserService)
                matchingRoutes(mockMatchingService)
                exchangeRoutes(mockExchangeService)
                ratingRoutes(mockRatingService)
            }
        }
    }

    @Test
    fun `Full User Flow - auth to profile to skills to search to matching to booking to complete to rating`() = testApplication {
        val userA = "user-alice-111"
        val userB = "user-bob-222"
        val tokenA = createToken(userA)

        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        // 1. Auth mocks
        coEvery { mockAuthService.register("alice@example.com", "Password@123", "Alice") } returns
                RegisterResponse(email = "alice@example.com", message = "Đăng ký thành công")
        coEvery { mockAuthService.login("alice@example.com", "Password@123") } returns
                AuthResponse(accessToken = tokenA, refreshToken = "refresh-alice", userId = userA, email = "alice@example.com")
        coEvery { mockAuthService.verifyOtp("alice@example.com", "123456", "signup") } returns
                AuthResponse(accessToken = tokenA, refreshToken = "refresh-alice", userId = userA, email = "alice@example.com")
        coEvery { mockAuthService.refreshToken("refresh-alice") } returns
                AuthResponse(accessToken = tokenA, refreshToken = "refresh-alice-2", userId = userA, email = "alice@example.com")

        // 2. Profile mocks
        val aliceProfile = ProfileDto(
            id = "p-1",
            userId = userA,
            fullName = "Alice Developer",
            bio = "Lập trình viên Kotlin",
            city = "Hà Nội"
        )
        every { mockProfileService.getProfile(userA) } returns aliceProfile
        every { mockProfileService.upsertProfile(userA, any()) } returns aliceProfile.copy(bio = "Senior Kotlin Dev")

        // 3. Skills mocks
        val categories = listOf(
            SkillCategoryDto(id = 1, name = "Lập trình", icon = "code", skills = listOf(SkillDto(id = 10, categoryId = 1, name = "Kotlin")))
        )
        every { mockSkillService.getCategories() } returns categories
        val userSkill = UserSkillDto(id = "us-1", skillId = 10, skillName = "Kotlin", categoryName = "Lập trình", type = "HAVE", proficiencyLevel = 4)
        every { mockSkillService.getUserSkills(userA) } returns listOf(userSkill)
        every { mockSkillService.addUserSkill(userA, any()) } returns userSkill

        // 4. Search mocks
        val searchItem = UserSearchDto(userId = userB, fullName = "Bob Designer", avatarUrl = null, bio = "UI/UX Designer")
        every { mockUserService.searchUsersWithCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                UserSearchResultDto(items = listOf(searchItem), nextCursor = null)

        // 5. Matching mocks
        val suggestion = MatchingSuggestionDto(
            userId = userB,
            fullName = "Bob Designer",
            avatarUrl = null,
            city = "Hà Nội",
            totalScore = 0.95,
            percentage = 95,
            breakdown = MatchingScoreBreakdown(skillMatch = 1.0, proficiencyFit = 1.0, locationBonus = 0.5, availabilityOverlap = 0.5),
            matchedSkills = MatchedSkillsBreakdown(theyTeachYou = emptyList(), youTeachThem = emptyList())
        )
        every { mockMatchingService.getSuggestions(userA, any()) } returns listOf(suggestion)

        // 6. Booking mocks
        val exchangeRequest = ExchangeRequestDto(
            id = "ex-flow-1",
            senderId = userA,
            receiverId = userB,
            senderName = "Alice",
            receiverName = "Bob",
            skillOfferedId = 10,
            skillOfferedName = "Kotlin",
            skillWantedId = 20,
            skillWantedName = "Figma",
            status = "PENDING",
            durationMinutes = 60,
            meetingMode = "ONLINE",
            scheduledAt = "2026-10-25T19:00:00Z",
            createdAt = "2026-10-19T08:00:00Z",
            updatedAt = "2026-10-19T08:00:00Z"
        )
        every { mockExchangeService.createExchangeRequest(userA, any()) } returns exchangeRequest
        every { mockExchangeService.getOutgoingRequests(userA) } returns listOf(exchangeRequest)
        every { mockExchangeService.acceptRequest(userB, "ex-flow-1") } returns exchangeRequest.copy(status = "ACCEPTED")

        // 7. Complete mocks
        every { mockExchangeService.completeRequest(userA, "ex-flow-1") } returns exchangeRequest.copy(status = "ACCEPTED", senderCompletedAt = "2026-10-25T20:00:00Z")
        every { mockExchangeService.completeRequest(userB, "ex-flow-1") } returns exchangeRequest.copy(status = "COMPLETED", senderCompletedAt = "2026-10-25T20:00:00Z", receiverCompletedAt = "2026-10-25T20:05:00Z")

        // 8. Rating mocks
        val createdRating = RatingDto(
            id = "rate-1",
            exchangeId = "ex-flow-1",
            reviewerId = userA,
            reviewerName = "Alice",
            revieweeId = userB,
            score = 5,
            comment = "Bob dạy rất tận tâm!",
            createdAt = "2026-10-25T20:10:00Z"
        )
        every { mockRatingService.createRating(userA, any()) } returns createdRating
        every { mockRatingService.getUserReputation(userB) } returns ReputationDto(score = 4.9, badge = "Xuất sắc", totalExchanges = 5, completionRate = 1.0, ratingCount = 5, avgRating = 4.9)

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // ── STEP 1: AUTH ────────────────────────────────────────────────────────
        val regResp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"alice@example.com","password":"Password@123","full_name":"Alice"}""")
        }
        assertEquals(HttpStatusCode.Created, regResp.status)

        val loginResp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"alice@example.com","password":"Password@123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)

        val refreshResp = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token":"refresh-alice"}""")
        }
        assertEquals(HttpStatusCode.OK, refreshResp.status)

        // ── STEP 2: PROFILE ─────────────────────────────────────────────────────
        val meResp = client.get("/api/profile/me") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, meResp.status)
        assertTrue(meResp.bodyAsText().contains("Alice Developer"))

        val updateResp = client.put("/api/profile") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"full_name":"Alice Developer","bio":"Senior Kotlin Dev"}""")
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)
        assertTrue(updateResp.bodyAsText().contains("Senior Kotlin Dev"))

        // ── STEP 3: SKILLS ──────────────────────────────────────────────────────
        val catResp = client.get("/api/skills/categories")
        assertEquals(HttpStatusCode.OK, catResp.status)
        assertTrue(catResp.bodyAsText().contains("Lập trình"))

        val addSkillResp = client.post("/api/skills/user") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"skill_id":10,"type":"HAVE","proficiency_level":4}""")
        }
        assertEquals(HttpStatusCode.Created, addSkillResp.status)

        val getSkillsResp = client.get("/api/skills/user") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, getSkillsResp.status)
        assertTrue(getSkillsResp.bodyAsText().contains("Kotlin"))

        // ── STEP 4: SEARCH ──────────────────────────────────────────────────────
        val searchResp = client.get("/api/users/search?q=Bob") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, searchResp.status)
        assertTrue(searchResp.bodyAsText().contains("Bob Designer"))

        // ── STEP 5: MATCHING ────────────────────────────────────────────────────
        val matchResp = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, matchResp.status)
        assertTrue(matchResp.bodyAsText().contains("Bob Designer"))

        // ── STEP 6: BOOKING ─────────────────────────────────────────────────────
        val bookingResp = client.post("/api/exchange-requests") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{
                "receiver_id":"$userB",
                "skill_offered_id":10,
                "skill_wanted_id":20,
                "scheduled_at":"2026-10-25T19:00:00Z",
                "duration_minutes":60,
                "meeting_mode":"ONLINE",
                "message":"Trao đổi Kotlin - Figma nhé"
            }""".trimIndent())
        }
        assertEquals(HttpStatusCode.Created, bookingResp.status)
        assertTrue(bookingResp.bodyAsText().contains("ex-flow-1"))

        val outgoingResp = client.get("/api/exchange-requests/outgoing") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, outgoingResp.status)

        // Bob accepts request
        val tokenB = createToken(userB)
        val acceptResp = client.put("/api/exchange-requests/ex-flow-1/accept") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.OK, acceptResp.status)
        assertTrue(acceptResp.bodyAsText().contains("ACCEPTED"))

        // ── STEP 7: COMPLETE ────────────────────────────────────────────────────
        val completeAlice = client.put("/api/exchange-requests/ex-flow-1/complete") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, completeAlice.status)

        val completeBob = client.put("/api/exchange-requests/ex-flow-1/complete") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
        }
        assertEquals(HttpStatusCode.OK, completeBob.status)
        assertTrue(completeBob.bodyAsText().contains("COMPLETED"))

        // ── STEP 8: RATING ──────────────────────────────────────────────────────
        val rateResp = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{
                "exchange_id":"ex-flow-1",
                "score":5,
                "comment":"Bob dạy rất tận tâm!"
            }""".trimIndent())
        }
        assertEquals(HttpStatusCode.Created, rateResp.status)
        assertTrue(rateResp.bodyAsText().contains("rate-1"))

        val repResp = client.get("/api/reputation/$userB") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
        }
        assertEquals(HttpStatusCode.OK, repResp.status)
        assertTrue(repResp.bodyAsText().contains("Xuất sắc"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ERROR BRANCHES: 401, 403, 404, 409, 422, 429
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Error branch - 401 Unauthorized when missing or invalid JWT`() = testApplication {
        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Calling protected endpoint without token
        val resNoAuth = client.get("/api/profile/me")
        assertEquals(HttpStatusCode.Unauthorized, resNoAuth.status)

        // Calling protected endpoint with invalid token
        val resBadAuth = client.get("/api/profile/me") {
            header(HttpHeaders.Authorization, "Bearer invalid-signature-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, resBadAuth.status)
    }

    @Test
    fun `Error branch - 403 Forbidden when unauthorized participant acts on exchange`() = testApplication {
        val userHacker = "user-hacker-999"
        val tokenHacker = createToken(userHacker)

        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        every { mockExchangeService.acceptRequest(userHacker, "ex-private-1") } throws
                ExchangeForbiddenException("Chỉ người nhận mới có quyền chấp nhận yêu cầu trao đổi")

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.put("/api/exchange-requests/ex-private-1/accept") {
            header(HttpHeaders.Authorization, "Bearer $tokenHacker")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("FORBIDDEN"))
    }

    @Test
    fun `Error branch - 404 Not Found when entity does not exist`() = testApplication {
        val userAlice = "user-alice-111"
        val tokenAlice = createToken(userAlice)

        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        every { mockRatingService.createRating(userAlice, any()) } throws
                RatingNotFoundException("Không tìm thấy trao đổi")

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer $tokenAlice")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id":"ex-not-found","score":5,"comment":"Tuyệt vời"}""")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun `Error branch - 409 Conflict when transition or action is invalid or duplicate`() = testApplication {
        val userAlice = "user-alice-111"
        val tokenAlice = createToken(userAlice)

        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        every { mockRatingService.createRating(userAlice, any()) } throws
                RatingConflictException("Bạn đã đánh giá trao đổi này rồi")

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer $tokenAlice")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id":"ex-1","score":5,"comment":"Đánh giá lần 2"}""")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertTrue(resp.bodyAsText().contains("ALREADY_RATED"))
    }

    @Test
    fun `Error branch - 422 Unprocessable Entity when validation fails`() = testApplication {
        val userAlice = "user-alice-111"
        val tokenAlice = createToken(userAlice)

        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        val mockAuthRateLimiter = AuthRateLimiter()
        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        every { mockRatingService.createRating(userAlice, any()) } throws
                ValidationException("Điểm đánh giá phải từ 1 đến 5")

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, mockAuthRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Rating with invalid score throws ValidationException -> returns 422
        val respRating = client.post("/api/ratings") {
            header(HttpHeaders.Authorization, "Bearer $tokenAlice")
            contentType(ContentType.Application.Json)
            setBody("""{"exchange_id":"ex-1","score":10,"comment":"Điểm quá cao"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, respRating.status)
        assertTrue(respRating.bodyAsText().contains("VALIDATION_FAILED"))

        // Register with name exceeding 100 characters returns 422
        val longName = "A".repeat(101)
        val respBadName = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"valid@example.com","password":"Password@123","full_name":"$longName"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, respBadName.status)
        assertTrue(respBadName.bodyAsText().contains("VALIDATION_FAILED"))
    }

    @Test
    fun `Error branch - 429 Too Many Requests when rate limit is exceeded`() = testApplication {
        val mockAuthService = mockk<AuthService>()
        val mockFirebaseTokenService = mockk<IFirebaseTokenService>(relaxed = true)
        // Rate limiter with max 2 login attempts per minute
        val strictRateLimiter = AuthRateLimiter(maxLoginPerMinute = 2)

        val mockProfileService = mockk<ProfileService>()
        val mockAvatarStorageService = mockk<IAvatarStorageService>(relaxed = true)
        val mockProfileRateLimiter = InMemoryRateLimiter()
        val mockSkillService = mockk<SkillService>()
        val mockUserService = mockk<UserService>()
        val mockMatchingService = mockk<MatchingService>()
        val mockExchangeService = mockk<ExchangeService>()
        val mockRatingService = mockk<RatingService>()

        coEvery { mockAuthService.login(any(), any()) } throws
                IllegalArgumentException("Sai email hoặc mật khẩu")

        setupIntegratedApp(
            mockAuthService, mockFirebaseTokenService, strictRateLimiter,
            mockProfileService, mockAvatarStorageService, mockProfileRateLimiter,
            mockSkillService, mockUserService, mockMatchingService,
            mockExchangeService, mockRatingService
        )

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Attempt 1: 401
        val r1 = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"rate@example.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r1.status)

        // Attempt 2: 401
        val r2 = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"rate@example.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r2.status)

        // Attempt 3: 429 Too Many Requests
        val r3 = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"rate@example.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, r3.status)
        assertTrue(r3.bodyAsText().contains("RATE_LIMITED"))
    }
}
