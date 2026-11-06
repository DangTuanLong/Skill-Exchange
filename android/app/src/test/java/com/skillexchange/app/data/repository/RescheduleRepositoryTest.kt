package com.skillexchange.app.data.repository

import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.data.remote.reschedule.CreateRescheduleProposalDto
import com.skillexchange.app.data.remote.reschedule.RescheduleProposalDto
import com.skillexchange.app.data.remote.reschedule.RescheduleRemoteDataSource
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal
import com.skillexchange.app.domain.model.reschedule.RescheduleStatus
import com.skillexchange.app.domain.model.reschedule.toDomain
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescheduleRepositoryTest {

    private val sampleProposalJson = """
    {
        "id": "prop-123",
        "exchange_id": "ex-456",
        "proposer_id": "user-sender-1",
        "new_scheduled_at": "2026-11-10T14:00:00Z",
        "reason": "Bận việc gia đình đột xuất",
        "status": "PENDING",
        "proposal_number": 1,
        "responded_at": null,
        "created_at": "2026-11-04T10:00:00Z",
        "updated_at": "2026-11-04T10:00:00Z"
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

    // ─── DTO to Domain Mapper Tests ──────────────────────────────────────────

    @Test
    fun toDomain_mapsAllFieldsAccurately() {
        val dto = RescheduleProposalDto(
            id = "prop-1",
            exchangeId = "ex-1",
            proposerId = "user-1",
            newScheduledAt = "2026-11-10T14:00:00Z",
            reason = "Dời lịch sang chiều",
            status = "PENDING",
            proposalNumber = 2,
            respondedAt = null,
            createdAt = "2026-11-04T10:00:00Z",
            updatedAt = "2026-11-04T10:00:00Z"
        )

        val domain = dto.toDomain()

        assertEquals("prop-1", domain.id)
        assertEquals("ex-1", domain.exchangeId)
        assertEquals("user-1", domain.proposerId)
        assertEquals("2026-11-10T14:00:00Z", domain.newScheduledAt)
        assertEquals("Dời lịch sang chiều", domain.reason)
        assertEquals(RescheduleStatus.PENDING, domain.status)
        assertEquals(2, domain.proposalNumber)
        assertNull(domain.respondedAt)
        assertEquals("2026-11-04T10:00:00Z", domain.createdAt)
        assertEquals("2026-11-04T10:00:00Z", domain.updatedAt)
    }

    @Test
    fun toDomain_mapsAllStatusesCorrectly() {
        val statuses = listOf(
            "PENDING" to RescheduleStatus.PENDING,
            "ACCEPTED" to RescheduleStatus.ACCEPTED,
            "REJECTED" to RescheduleStatus.REJECTED,
            "CANCELLED" to RescheduleStatus.CANCELLED,
            "EXPIRED" to RescheduleStatus.EXPIRED,
            "UNKNOWN_VALUE" to RescheduleStatus.PENDING
        )

        for ((inputStatus, expectedEnum) in statuses) {
            val dto = RescheduleProposalDto(
                id = "prop-test",
                exchangeId = "ex-test",
                proposerId = "user-test",
                newScheduledAt = "2026-11-10T14:00:00Z",
                status = inputStatus,
                proposalNumber = 1,
                createdAt = "2026-11-04T10:00:00Z",
                updatedAt = "2026-11-04T10:00:00Z"
            )
            assertEquals(
                "Status $inputStatus should map to $expectedEnum",
                expectedEnum,
                dto.toDomain().status
            )
        }
    }

    @Test
    fun domainHelperMethods_workAsExpected() {
        val proposal = RescheduleProposal(
            id = "p-1",
            exchangeId = "ex-1",
            proposerId = "user-a",
            newScheduledAt = "2026-11-10T14:00:00Z",
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = "2026-11-04T10:00:00Z",
            updatedAt = "2026-11-04T10:00:00Z"
        )

        assertTrue(proposal.isProposer("user-a"))
        assertFalse(proposal.isProposer("user-b"))
        assertTrue(proposal.isPending())

        val acceptedProposal = proposal.copy(status = RescheduleStatus.ACCEPTED)
        assertFalse(acceptedProposal.isPending())

        assertEquals("Chờ phản hồi", RescheduleStatus.PENDING.displayName())
        assertEquals("Đã chấp nhận", RescheduleStatus.ACCEPTED.displayName())
        assertEquals("Đã từ chối", RescheduleStatus.REJECTED.displayName())
        assertEquals("Đã hủy", RescheduleStatus.CANCELLED.displayName())
        assertEquals("Đã hết hạn", RescheduleStatus.EXPIRED.displayName())
    }

    // ─── Repository Remote Calls Tests ───────────────────────────────────────

    @Test
    fun createProposal_Success_ReturnsProposal() = runTest {
        val client = createMockClient(HttpStatusCode.Created, wrapData(sampleProposalJson))
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.createProposal(
            exchangeId = "ex-456",
            dto = CreateRescheduleProposalDto(
                newScheduledAt = "2026-11-10T14:00:00Z",
                reason = "Bận việc gia đình đột xuất"
            )
        )

        assertTrue(result.isSuccess)
        val proposal = result.getOrNull()
        assertNotNull(proposal)
        assertEquals("prop-123", proposal?.id)
        assertEquals("ex-456", proposal?.exchangeId)
        assertEquals(RescheduleStatus.PENDING, proposal?.status)
        assertEquals(1, proposal?.proposalNumber)
    }

    @Test
    fun createProposal_ConvenienceOverload_WorksIdentically() = runTest {
        val client = createMockClient(HttpStatusCode.Created, wrapData(sampleProposalJson))
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.createProposal(
            exchangeId = "ex-456",
            newScheduledAt = "2026-11-10T14:00:00Z",
            reason = "Bận việc gia đình đột xuất"
        )

        assertTrue(result.isSuccess)
        assertEquals("prop-123", result.getOrNull()?.id)
    }

    @Test
    fun createProposal_LimitExceeded_ReturnsFailureWithApiException() = runTest {
        val errorJson = """
        {
            "success": false,
            "status": 409,
            "code": "RESCHEDULE_LIMIT_EXCEEDED",
            "message": "Đã đạt giới hạn tối đa 3 lần đề xuất đổi lịch cho buổi trao đổi này."
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.Conflict, errorJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.createProposal(
            exchangeId = "ex-456",
            newScheduledAt = "2026-11-10T14:00:00Z"
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException)
        val apiException = ex as ApiException
        assertEquals(409, apiException.status)
        assertEquals(ApiException.CODE_RESCHEDULE_LIMIT_EXCEEDED, apiException.code)
        assertEquals("Đã đạt giới hạn tối đa 3 lần đề xuất đổi lịch cho buổi trao đổi này.", apiException.message)
    }

    @Test
    fun getCurrentProposal_Success_ReturnsProposal() = runTest {
        val client = createMockClient(HttpStatusCode.OK, wrapData(sampleProposalJson))
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.getCurrentProposal("ex-456")

        assertTrue(result.isSuccess)
        val proposal = result.getOrNull()
        assertNotNull(proposal)
        assertEquals("prop-123", proposal?.id)
        assertEquals(RescheduleStatus.PENDING, proposal?.status)
    }

    @Test
    fun getCurrentProposal_NullData_ReturnsSuccessNull() = runTest {
        val client = createMockClient(HttpStatusCode.OK, """{"success":true,"data":null}""")
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.getCurrentProposal("ex-456")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun getCurrentProposal_NotParticipant_ReturnsFailureWithApiException() = runTest {
        val errorJson = """
        {
            "success": false,
            "status": 403,
            "code": "NOT_PARTICIPANT",
            "message": "Bạn không phải là người tham gia buổi trao đổi này."
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.Forbidden, errorJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.getCurrentProposal("ex-456")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as ApiException
        assertEquals(403, ex.status)
        assertEquals(ApiException.CODE_NOT_PARTICIPANT, ex.code)
        assertEquals("Bạn không phải là người tham gia buổi trao đổi này.", ex.message)
    }

    @Test
    fun acceptProposal_Success_ReturnsAcceptedProposal() = runTest {
        val acceptedJson = wrapData(
            sampleProposalJson
                .replace("\"PENDING\"", "\"ACCEPTED\"")
                .replace("\"responded_at\": null", "\"responded_at\": \"2026-11-04T11:00:00Z\"")
        )
        val client = createMockClient(HttpStatusCode.OK, acceptedJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.acceptProposal("prop-123")

        assertTrue(result.isSuccess)
        val proposal = result.getOrNull()
        assertEquals(RescheduleStatus.ACCEPTED, proposal?.status)
        assertEquals("2026-11-04T11:00:00Z", proposal?.respondedAt)
    }

    @Test
    fun rejectProposal_Success_ReturnsRejectedProposal() = runTest {
        val rejectedJson = wrapData(
            sampleProposalJson
                .replace("\"PENDING\"", "\"REJECTED\"")
                .replace("\"responded_at\": null", "\"responded_at\": \"2026-11-04T11:00:00Z\"")
        )
        val client = createMockClient(HttpStatusCode.OK, rejectedJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.rejectProposal("prop-123")

        assertTrue(result.isSuccess)
        val proposal = result.getOrNull()
        assertEquals(RescheduleStatus.REJECTED, proposal?.status)
        assertEquals("2026-11-04T11:00:00Z", proposal?.respondedAt)
    }

    @Test
    fun cancelProposal_Success_ReturnsCancelledProposal() = runTest {
        val cancelledJson = wrapData(
            sampleProposalJson
                .replace("\"PENDING\"", "\"CANCELLED\"")
        )
        val client = createMockClient(HttpStatusCode.OK, cancelledJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.cancelProposal("prop-123")

        assertTrue(result.isSuccess)
        val proposal = result.getOrNull()
        assertEquals(RescheduleStatus.CANCELLED, proposal?.status)
    }

    @Test
    fun acceptProposal_Conflict_ReturnsFailureWithApiException() = runTest {
        val errorJson = """
        {
            "success": false,
            "status": 409,
            "code": "PROPOSAL_NOT_PENDING",
            "message": "Đề xuất đổi lịch không còn ở trạng thái chờ phản hồi."
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.Conflict, errorJson)
        val repo = RescheduleRepositoryImpl(RescheduleRemoteDataSource(client))

        val result = repo.acceptProposal("prop-123")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as ApiException
        assertEquals(409, ex.status)
        assertEquals(ApiException.CODE_PROPOSAL_NOT_PENDING, ex.code)
        assertEquals("Đề xuất đổi lịch không còn ở trạng thái chờ phản hồi.", ex.message)
    }
}

