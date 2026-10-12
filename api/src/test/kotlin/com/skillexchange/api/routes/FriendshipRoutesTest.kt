package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.friendship.FriendDto
import com.skillexchange.api.models.friendship.FriendshipDto
import com.skillexchange.api.models.friendship.FriendshipStatusDto
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.friendship.FriendshipConflictException
import com.skillexchange.api.services.friendship.FriendshipForbiddenException
import com.skillexchange.api.services.friendship.FriendshipNotFoundException
import com.skillexchange.api.services.friendship.FriendshipService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendshipRoutesTest {

    private val mockFriendshipService = mockk<FriendshipService>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        clearMocks(mockFriendshipService)
    }

    private fun createTestJwt(userId: String = "user-caller-111"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun ApplicationTestBuilder.setupFriendshipRoutesModule() {
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
                        if (credential.payload.subject != null) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                friendshipRoutes(mockFriendshipService)
            }
        }
    }

    @Test
    fun `POST friendship returns 201 Created on valid request`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val friendId = "user-friend-222"
        val expectedDto = FriendshipDto(
            id = "friendship-uuid-1",
            userId = callerId,
            friendId = friendId,
            status = "PENDING",
            createdAt = "2026-10-12T20:00:00",
            updatedAt = "2026-10-12T20:00:00"
        )

        every { mockFriendshipService.sendRequest(callerId, friendId) } returns expectedDto

        val response = client.post("/api/friendships") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"friend_id":"$friendId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("friendship-uuid-1"))
        assertTrue(body.contains("PENDING"))
    }

    @Test
    fun `POST friendship returns 422 when self friendship is attempted`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        every { mockFriendshipService.sendRequest(callerId, callerId) } throws ValidationException("Không thể gửi lời mời kết bạn cho chính mình.")

        val response = client.post("/api/friendships") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"friend_id":"$callerId"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_FAILED, error.code)
    }

    @Test
    fun `POST friendship returns 404 when target user does not exist`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val nonExistentId = "user-non-existent"
        every { mockFriendshipService.sendRequest(callerId, nonExistentId) } throws FriendshipNotFoundException("Không tìm thấy người dùng.")

        val response = client.post("/api/friendships") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"friend_id":"$nonExistentId"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.NOT_FOUND, error.code)
    }

    @Test
    fun `PUT accept returns 200 OK when receiver accepts`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-receiver-222"
        val friendshipId = "friendship-uuid-1"
        val acceptedDto = FriendshipDto(
            id = friendshipId,
            userId = "user-sender-111",
            friendId = callerId,
            status = "ACCEPTED",
            createdAt = "2026-10-12T20:00:00",
            updatedAt = "2026-10-12T20:05:00"
        )

        every { mockFriendshipService.acceptRequest(callerId, friendshipId) } returns acceptedDto

        val response = client.put("/api/friendships/$friendshipId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ACCEPTED"))
    }

    @Test
    fun `PUT accept returns 403 Forbidden when sender attempts to accept own request`() = testApplication {
        setupFriendshipRoutesModule()

        val senderCallerId = "user-sender-111"
        val friendshipId = "friendship-uuid-1"

        every { mockFriendshipService.acceptRequest(senderCallerId, friendshipId) } throws FriendshipForbiddenException("Chỉ người nhận mới có quyền chấp nhận lời mời kết bạn.")

        val response = client.put("/api/friendships/$friendshipId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(senderCallerId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, error.code)
    }

    @Test
    fun `PUT accept returns 409 Conflict when request was already rejected`() = testApplication {
        setupFriendshipRoutesModule()

        val receiverId = "user-receiver-222"
        val friendshipId = "friendship-uuid-1"

        every { mockFriendshipService.acceptRequest(receiverId, friendshipId) } throws FriendshipConflictException("Lời mời kết bạn này đã bị từ chối trước đó.")

        val response = client.put("/api/friendships/$friendshipId/accept") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(receiverId)}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.CONFLICT, error.code)
    }

    @Test
    fun `PUT reject returns 200 OK when receiver rejects`() = testApplication {
        setupFriendshipRoutesModule()

        val receiverId = "user-receiver-222"
        val friendshipId = "friendship-uuid-1"
        val rejectedDto = FriendshipDto(
            id = friendshipId,
            userId = "user-sender-111",
            friendId = receiverId,
            status = "REJECTED",
            createdAt = "2026-10-12T20:00:00",
            updatedAt = "2026-10-12T20:05:00"
        )

        every { mockFriendshipService.rejectRequest(receiverId, friendshipId) } returns rejectedDto

        val response = client.put("/api/friendships/$friendshipId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(receiverId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("REJECTED"))
    }

    @Test
    fun `PUT reject returns 403 Forbidden when sender attempts to reject`() = testApplication {
        setupFriendshipRoutesModule()

        val senderId = "user-sender-111"
        val friendshipId = "friendship-uuid-1"

        every { mockFriendshipService.rejectRequest(senderId, friendshipId) } throws FriendshipForbiddenException("Chỉ người nhận mới có quyền từ chối lời mời kết bạn.")

        val response = client.put("/api/friendships/$friendshipId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(senderId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, error.code)
    }

    @Test
    fun `DELETE friendship returns 200 OK when participant removes`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val friendshipId = "friendship-uuid-1"

        every { mockFriendshipService.removeFriendship(callerId, friendshipId) } returns true

        val response = client.delete("/api/friendships/$friendshipId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("thành công"))
    }

    @Test
    fun `DELETE friendship returns 403 Forbidden when non-participant attempts removal`() = testApplication {
        setupFriendshipRoutesModule()

        val strangerId = "user-stranger-999"
        val friendshipId = "friendship-uuid-1"

        every { mockFriendshipService.removeFriendship(strangerId, friendshipId) } throws FriendshipForbiddenException("Bạn không có quyền xóa quan hệ bạn bè này.")

        val response = client.delete("/api/friendships/$friendshipId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(strangerId)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.FORBIDDEN, error.code)
    }

    @Test
    fun `GET me returns friends list`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val friends = listOf(
            FriendDto(
                friendshipId = "f-1",
                userId = "user-friend-222",
                fullName = "Nguyễn Văn B",
                avatarUrl = "https://example.com/avatar.jpg",
                city = "Hà Nội",
                bio = "Bio",
                status = "ACCEPTED",
                createdAt = "2026-10-12T20:00:00"
            )
        )

        every { mockFriendshipService.getFriends(callerId) } returns friends

        val response = client.get("/api/friendships/me") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Nguyễn Văn B"))
        assertTrue(body.contains("user-friend-222"))
    }

    @Test
    fun `GET pending returns pending requests list`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val pending = listOf(
            FriendDto(
                friendshipId = "f-2",
                userId = "user-sender-333",
                fullName = "Trần Thị C",
                avatarUrl = null,
                city = "TP.HCM",
                bio = null,
                status = "PENDING",
                createdAt = "2026-10-12T20:01:00"
            )
        )

        every { mockFriendshipService.getPendingRequests(callerId) } returns pending

        val response = client.get("/api/friendships/pending") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Trần Thị C"))
    }

    @Test
    fun `GET status returns current relationship status`() = testApplication {
        setupFriendshipRoutesModule()

        val callerId = "user-caller-111"
        val targetId = "user-friend-222"
        val statusDto = FriendshipStatusDto(
            status = "PENDING_SENT",
            friendshipId = "f-1"
        )

        every { mockFriendshipService.getFriendshipStatus(callerId, targetId) } returns statusDto

        val response = client.get("/api/friendships/status/$targetId") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt(callerId)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("PENDING_SENT"))
        assertTrue(response.bodyAsText().contains("f-1"))
    }
}
