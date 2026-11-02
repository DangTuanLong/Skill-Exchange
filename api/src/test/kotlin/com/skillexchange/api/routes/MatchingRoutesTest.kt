package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.matching.*
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.matching.MatchingService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchingRoutesTest {

    private val mockMatchingService = mockk<MatchingService>(relaxed = true)

    @BeforeTest
    fun setUp() {
        clearMocks(mockMatchingService)
    }

    private fun createTestJwt(userId: String = "user-caller-123"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.setupMatchingRoutesModule() {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = "SkillExchange API"
                    verifier(
                        JWT.require(Algorithm.HMAC256("test-secret"))
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
                matchingRoutes(mockMatchingService)
            }
        }
    }

    @Test
    fun `GET suggestions - returns 401 without JWT`() = testApplication {
        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET suggestions - returns 200 with suggestions list`() = testApplication {
        val callerId = "user-caller-123"
        val expectedList = listOf(
            MatchingSuggestionDto(
                userId = "user-cand-1",
                fullName = "Nguyen B",
                avatarUrl = null,
                city = "Hà Nội",
                totalScore = 0.85,
                percentage = 85,
                breakdown = MatchingScoreBreakdown(
                    skillMatch = 1.0,
                    proficiencyFit = 1.0,
                    locationBonus = 1.0,
                    availabilityOverlap = 0.25
                ),
                matchedSkills = MatchedSkillsBreakdown(
                    theyTeachYou = listOf(MatchedSkillDto(1, "Kotlin", 4, 3)),
                    youTeachThem = listOf(MatchedSkillDto(2, "English", 3, 2))
                )
            )
        )

        every { mockMatchingService.getSuggestions(callerId, 20) } returns expectedList

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("user-cand-1"))
        assertTrue(body.contains("Nguyen B"))
        assertTrue(body.contains("85"))

        verify { mockMatchingService.getSuggestions(callerId, 20) }
    }

    @Test
    fun `GET suggestions - clamps limit parameter between 1 and 50`() = testApplication {
        val callerId = "user-caller-123"
        every { mockMatchingService.getSuggestions(callerId, 50) } returns emptyList()

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions?limit=100") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockMatchingService.getSuggestions(callerId, 50) }
    }

    @Test
    fun `GET score - returns 401 without JWT`() = testApplication {
        setupMatchingRoutesModule()

        val response = client.get("/api/matching/score/target-user-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET score - returns 404 when targetUserId is the caller themselves`() = testApplication {
        val callerId = "user-caller-123"
        setupMatchingRoutesModule()

        val response = client.get("/api/matching/score/$callerId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET score - returns 404 when score cannot be calculated (e_g_ SkillMatch = 0)`() = testApplication {
        val callerId = "user-caller-123"
        val targetId = "user-target-456"
        every { mockMatchingService.getScore(callerId, targetId) } returns null

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/score/$targetId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Không tìm thấy"))
    }

    @Test
    fun `GET score - returns 200 with breakdown and matched skills`() = testApplication {
        val callerId = "user-caller-123"
        val targetId = "user-target-456"
        val score = MatchingScoreResponse(
            targetUserId = targetId,
            totalScore = 0.92,
            percentage = 92,
            breakdown = MatchingScoreBreakdown(
                skillMatch = 1.0,
                proficiencyFit = 1.0,
                locationBonus = 1.0,
                availabilityOverlap = 0.6
            ),
            matchedSkills = MatchedSkillsBreakdown(
                theyTeachYou = listOf(MatchedSkillDto(1, "Python", 5, 3)),
                youTeachThem = listOf(MatchedSkillDto(2, "Guitar", 4, 2))
            )
        )
        every { mockMatchingService.getScore(callerId, targetId) } returns score

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/score/$targetId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("target-456"))
        assertTrue(body.contains("0.92"))
        assertTrue(body.contains("Python"))
        assertTrue(body.contains("Guitar"))
    }

    @Test
    fun `GET suggestions - passes cursor parameter to service`() = testApplication {
        val callerId = "user-caller-123"
        val cursor = "ODVfdXNlci1jYW5kLTE"
        every { mockMatchingService.getSuggestions(callerId, 20, cursor) } returns emptyList()

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions?cursor=$cursor") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockMatchingService.getSuggestions(callerId, 20, cursor) }
    }

    @Test
    fun `GET suggestions potential - returns 401 without JWT`() = testApplication {
        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions/potential")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET suggestions potential - returns 200 with potential matches`() = testApplication {
        val callerId = "user-caller-123"
        val expected = listOf(
            PotentialMatchDto(
                userId = "cand-pot-1",
                fullName = "Pham C",
                avatarUrl = null,
                city = "Hà Nội",
                canTeachSkill = PotentialMatchSkillDto(skillId = 1, skillName = "Kotlin", proficiencyLevel = 4),
                suggestedWantSkill = PotentialMatchSkillDto(skillId = 5, skillName = "Design", proficiencyLevel = 3),
                potentialScore = 78
            )
        )
        every { mockMatchingService.getPotentialSuggestions(callerId, 10, null) } returns expected

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions/potential") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("cand-pot-1"))
        assertTrue(body.contains("Pham C"))
        assertTrue(body.contains("78"))
        verify { mockMatchingService.getPotentialSuggestions(callerId, 10, null) }
    }

    @Test
    fun `GET suggestions potential - clamps limit between 1 and 50 and passes cursor`() = testApplication {
        val callerId = "user-caller-123"
        val cursor = "cursor123"
        every { mockMatchingService.getPotentialSuggestions(callerId, 50, cursor) } returns emptyList()

        setupMatchingRoutesModule()

        val response = client.get("/api/matching/suggestions/potential?limit=100&cursor=$cursor") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify { mockMatchingService.getPotentialSuggestions(callerId, 50, cursor) }
    }
}
