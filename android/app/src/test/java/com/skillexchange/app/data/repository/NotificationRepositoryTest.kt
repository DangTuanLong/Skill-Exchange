package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.notification.NotificationRemoteDataSource
import com.skillexchange.app.domain.model.notification.NotificationType
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

class NotificationRepositoryTest {

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

    private val sampleNotificationsJson = """
    {
        "notifications": [
            {
                "id": "notif-1",
                "userId": "user-123",
                "type": "NEW_REQUEST",
                "title": "Yêu cầu trao đổi mới",
                "body": "Nguyễn Văn A muốn trao đổi kỹ năng với bạn",
                "entityId": "ex-999",
                "data": "{}",
                "isRead": false,
                "createdAt": "2026-10-19T08:00:00Z"
            },
            {
                "id": "notif-2",
                "userId": "user-123",
                "type": "NEW_MESSAGE",
                "title": "Tin nhắn mới",
                "body": "Chào bạn nhé!",
                "entityId": "chat-888",
                "data": "{}",
                "isRead": true,
                "createdAt": "2026-10-19T07:00:00Z"
            }
        ],
        "unreadCount": 1,
        "total": 2
    }
    """.trimIndent()

    @Test
    fun getNotifications_Success_ReturnsNotificationListResult() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleNotificationsJson)
        val repo = NotificationRepositoryImpl(NotificationRemoteDataSource(client))

        val result = repo.getNotifications(page = 1, limit = 20)

        assertTrue(result.isSuccess)
        val listResult = result.getOrNull()
        assertNotNull(listResult)
        assertEquals(2, listResult?.notifications?.size)
        assertEquals(1L, listResult?.unreadCount)
        assertEquals(2L, listResult?.total)

        val firstNotif = listResult?.notifications?.first()
        assertEquals("notif-1", firstNotif?.id)
        assertEquals(NotificationType.NEW_REQUEST, firstNotif?.type)
        assertFalse(firstNotif?.isRead ?: true)
        assertEquals("ex-999", firstNotif?.entityId)
    }

    @Test
    fun markAsRead_Success_ReturnsUnit() = runTest {
        val json = """{"success":true,"message":"Đã đánh dấu đã đọc"}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val repo = NotificationRepositoryImpl(NotificationRemoteDataSource(client))

        val result = repo.markAsRead("notif-1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun markAllAsRead_Success_ReturnsUnit() = runTest {
        val json = """{"success":true,"message":"Đã đánh dấu tất cả đã đọc"}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val repo = NotificationRepositoryImpl(NotificationRemoteDataSource(client))

        val result = repo.markAllAsRead()

        assertTrue(result.isSuccess)
    }

    @Test
    fun getNotifications_Error_ReturnsFailure() = runTest {
        val errorJson = """{"success":false,"code":"UNAUTHORIZED","message":"Chưa đăng nhập"}"""
        val client = createMockClient(HttpStatusCode.Unauthorized, errorJson)
        val repo = NotificationRepositoryImpl(NotificationRemoteDataSource(client))

        val result = repo.getNotifications(page = 1, limit = 20)

        assertTrue(result.isFailure)
    }
}
