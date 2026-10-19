package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.data.remote.exchange.ExchangeRemoteDataSource
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
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

class ExchangeRepositoryTest {

    private val sampleExchangeJson = """
    {
        "id": "ex-123",
        "sender_id": "user-sender-1",
        "receiver_id": "user-receiver-2",
        "sender_name": "Nguyễn Văn Sender",
        "sender_avatar_url": "https://example.com/avatar1.jpg",
        "receiver_name": "Trần Thị Receiver",
        "receiver_avatar_url": "https://example.com/avatar2.jpg",
        "skill_offered_id": 1,
        "skill_offered_name": "Kotlin Android",
        "skill_wanted_id": 2,
        "skill_wanted_name": "Python Machine Learning",
        "status": "PENDING",
        "duration_minutes": 60,
        "meeting_mode": "ONLINE",
        "message": "Trao đổi 1 buổi học nhé",
        "cancellation_reason": null,
        "scheduled_at": "2026-10-25T19:00:00Z",
        "sender_completed_at": null,
        "receiver_completed_at": null,
        "created_at": "2026-10-19T08:00:00Z",
        "updated_at": "2026-10-19T08:00:00Z"
    }
    """.trimIndent()

    private fun wrapData(json: String): String = """{"success":true,"data":$json}"""

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
    fun createExchangeRequest_Success_ReturnsExchangeRequest() = runTest {
        val client = createMockClient(HttpStatusCode.Created, wrapData(sampleExchangeJson))
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val dto = CreateExchangeRequestDto(
            receiverId = "user-receiver-2",
            skillOfferedId = 1,
            skillWantedId = 2,
            scheduledAt = "2026-10-25T19:00:00Z",
            durationMinutes = 60,
            meetingMode = "ONLINE",
            message = "Trao đổi 1 buổi học nhé"
        )
        val result = repo.createExchangeRequest(dto)

        assertTrue(result.isSuccess)
        val exchange = result.getOrNull()
        assertNotNull(exchange)
        assertEquals("ex-123", exchange?.id)
        assertEquals("user-sender-1", exchange?.senderId)
        assertEquals("user-receiver-2", exchange?.receiverId)
        assertEquals(ExchangeStatus.PENDING, exchange?.status)
        assertEquals(MeetingMode.ONLINE, exchange?.meetingMode)
    }

    @Test
    fun getIncomingRequests_Success_ReturnsList() = runTest {
        val json = wrapData("[$sampleExchangeJson]")
        val client = createMockClient(HttpStatusCode.OK, json)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.getIncomingRequests()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("ex-123", result.getOrNull()?.first()?.id)
    }

    @Test
    fun getOutgoingRequests_Success_ReturnsList() = runTest {
        val json = wrapData("[$sampleExchangeJson]")
        val client = createMockClient(HttpStatusCode.OK, json)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.getOutgoingRequests()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun getExchangeRequest_Success_ReturnsExchange() = runTest {
        val client = createMockClient(HttpStatusCode.OK, wrapData(sampleExchangeJson))
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.getExchangeRequest("ex-123")

        assertTrue(result.isSuccess)
        assertEquals("ex-123", result.getOrNull()?.id)
    }

    @Test
    fun acceptRequest_Success_ReturnsAcceptedStatus() = runTest {
        val acceptedJson = wrapData(sampleExchangeJson.replace("\"PENDING\"", "\"ACCEPTED\""))
        val client = createMockClient(HttpStatusCode.OK, acceptedJson)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.acceptRequest("ex-123")

        assertTrue(result.isSuccess)
        assertEquals(ExchangeStatus.ACCEPTED, result.getOrNull()?.status)
    }

    @Test
    fun rejectRequest_Success_ReturnsRejectedStatus() = runTest {
        val rejectedJson = wrapData(sampleExchangeJson.replace("\"PENDING\"", "\"REJECTED\""))
        val client = createMockClient(HttpStatusCode.OK, rejectedJson)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.rejectRequest("ex-123")

        assertTrue(result.isSuccess)
        assertEquals(ExchangeStatus.REJECTED, result.getOrNull()?.status)
    }

    @Test
    fun cancelRequest_Success_ReturnsCancelledStatus() = runTest {
        val cancelledJson = wrapData(sampleExchangeJson.replace("\"PENDING\"", "\"CANCELLED\""))
        val client = createMockClient(HttpStatusCode.OK, cancelledJson)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.cancelRequest("ex-123", "Bận đột xuất")

        assertTrue(result.isSuccess)
        assertEquals(ExchangeStatus.CANCELLED, result.getOrNull()?.status)
    }

    @Test
    fun completeRequest_Success_ReturnsCompletedStatus() = runTest {
        val completedJson = wrapData(sampleExchangeJson.replace("\"PENDING\"", "\"COMPLETED\""))
        val client = createMockClient(HttpStatusCode.OK, completedJson)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.completeRequest("ex-123")

        assertTrue(result.isSuccess)
        assertEquals(ExchangeStatus.COMPLETED, result.getOrNull()?.status)
    }

    @Test
    fun getExchangeRequest_Error_ReturnsFailure() = runTest {
        val errorJson = """{"success":false,"code":"NOT_FOUND","message":"Không tìm thấy yêu cầu trao đổi"}"""
        val client = createMockClient(HttpStatusCode.NotFound, errorJson)
        val repo = ExchangeRepositoryImpl(ExchangeRemoteDataSource(client))

        val result = repo.getExchangeRequest("ex-not-exist")

        assertTrue(result.isFailure)
    }
}
