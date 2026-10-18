package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.notification.NotificationDto
import com.skillexchange.api.models.notification.NotificationListResponseDto
import com.skillexchange.api.models.notification.ProcessRemindersResponseDto
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.notification.NotificationService
import com.skillexchange.api.services.notification.ReminderSchedulerService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
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
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRoutesTest {

    private val mockNotificationService = mockk<NotificationService>(relaxed = true)
    private val mockReminderSchedulerService = mockk<ReminderSchedulerService>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        clearMocks(mockNotificationService, mockReminderSchedulerService)
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("JOB_SECRET")
    }

    private fun createTestJwt(userId: String = "test-user-id"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun ApplicationTestBuilder.setupNotificationRoutesModule(
        reminderRateLimiter: com.skillexchange.api.services.notification.ReminderRateLimiter = com.skillexchange.api.services.notification.ReminderRateLimiter()
    ) {
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
                        val sub = credential.payload.subject
                        if (!sub.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing {
                notificationRoutes(mockNotificationService, mockReminderSchedulerService, reminderRateLimiter)
            }
        }
    }

    @Test
    fun `GET notifications returns 200 and list of notifications with unread count`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val testNotifications = listOf(
            NotificationDto(
                id = "n-1",
                userId = "test-user-id",
                type = "NEW_REQUEST",
                title = "Yêu cầu trao đổi mới",
                body = "Nam muốn trao đổi Kotlin",
                entityId = "ex-1",
                data = "{}",
                isRead = false,
                createdAt = "2026-10-10T10:00:00"
            )
        )

        every {
            mockNotificationService.getNotifications("test-user-id", page = 1, limit = 30)
        } returns NotificationListResponseDto(
            notifications = testNotifications,
            unreadCount = 1,
            total = 1
        )

        val response = client.get("/api/notifications") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("test-user-id")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val bodyText = response.bodyAsText()
        val result = json.decodeFromString<NotificationListResponseDto>(bodyText)
        assertEquals(1, result.notifications.size)
        assertEquals(1L, result.unreadCount)
        assertEquals("n-1", result.notifications[0].id)
    }

    @Test
    fun `GET notifications returns 400 when pagination is invalid`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/notifications?page=0&limit=30") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT notification read marks single notification as read`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        every { mockNotificationService.markAsRead("test-user-id", "n-123") } returns true

        val response = client.put("/api/notifications/n-123/read") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("test-user-id")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { mockNotificationService.markAsRead("test-user-id", "n-123") }
    }

    @Test
    fun `PUT notification read returns 404 when notification not found or unowned`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        every { mockNotificationService.markAsRead("test-user-id", "n-not-found") } returns false

        val response = client.put("/api/notifications/n-not-found/read") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("test-user-id")}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT notification read-all marks all notifications as read`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        every { mockNotificationService.markAllAsRead("test-user-id") } returns 5

        val response = client.put("/api/notifications/read-all") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("test-user-id")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { mockNotificationService.markAllAsRead("test-user-id") }
    }

    @Test
    fun `POST internal process-reminders returns 401 when X-Job-Secret is missing or incorrect`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        System.setProperty("JOB_SECRET", "super-secret-123")

        // 1. Missing header
        val resMissing = client.post("/api/internal/jobs/process-reminders")
        assertEquals(HttpStatusCode.Unauthorized, resMissing.status)

        // 2. Incorrect secret
        val resWrong = client.post("/api/internal/jobs/process-reminders") {
            header("X-Job-Secret", "wrong-secret")
        }
        assertEquals(HttpStatusCode.Unauthorized, resWrong.status)

        verify(exactly = 0) { mockReminderSchedulerService.processReminders() }
    }

    @Test
    fun `POST internal process-reminders returns 200 when X-Job-Secret matches`() = testApplication {
        setupNotificationRoutesModule()
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        System.setProperty("JOB_SECRET", "super-secret-123")

        every { mockReminderSchedulerService.processReminders() } returns ProcessRemindersResponseDto(
            success = true,
            appointmentRemindersSent = 2,
            ratingRemindersSent = 1,
            message = "Success"
        )

        val response = client.post("/api/internal/jobs/process-reminders") {
            header("X-Job-Secret", "super-secret-123")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { mockReminderSchedulerService.processReminders() }

        val body = json.decodeFromString<ProcessRemindersResponseDto>(response.bodyAsText())
        assertTrue(body.success)
        assertEquals(2, body.appointmentRemindersSent)
        assertEquals(1, body.ratingRemindersSent)
    }

    @Test
    fun `POST internal process-reminders returns 429 when rate limit is exceeded`() = testApplication {
        val testLimiter = com.skillexchange.api.services.notification.ReminderRateLimiter(maxPerMinute = 2)
        setupNotificationRoutesModule(testLimiter)
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        System.setProperty("JOB_SECRET", "super-secret-123")
        every { mockReminderSchedulerService.processReminders() } returns ProcessRemindersResponseDto(
            success = true,
            appointmentRemindersSent = 0,
            ratingRemindersSent = 0,
            message = "OK"
        )

        // 2 calls succeed
        val res1 = client.post("/api/internal/jobs/process-reminders") { header("X-Job-Secret", "super-secret-123") }
        assertEquals(HttpStatusCode.OK, res1.status)
        val res2 = client.post("/api/internal/jobs/process-reminders") { header("X-Job-Secret", "super-secret-123") }
        assertEquals(HttpStatusCode.OK, res2.status)

        // 3rd call is rate limited
        val res3 = client.post("/api/internal/jobs/process-reminders") { header("X-Job-Secret", "super-secret-123") }
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)
        val body = res3.bodyAsText()
        assertTrue(body.contains("RATE_LIMITED"))
        assertTrue(body.contains("429"))
    }
}
