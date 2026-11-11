package com.skillexchange.app.presentation.booking

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.data.remote.reschedule.CreateRescheduleProposalDto
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal
import com.skillexchange.app.domain.model.reschedule.RescheduleStatus
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IRescheduleRepository
import com.skillexchange.app.presentation.booking.detail.BookingDetailEffect
import com.skillexchange.app.presentation.booking.detail.BookingDetailIntent
import com.skillexchange.app.presentation.booking.detail.BookingDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_receiver") }

    private var currentExchange = ExchangeRequest(
        id = "ex_999",
        senderId = "user_sender",
        receiverId = "user_receiver",
        senderName = "Sender Guy",
        receiverName = "Receiver Girl",
        skillOfferedId = 1,
        skillOfferedName = "Kotlin",
        skillWantedId = 2,
        skillWantedName = "Design",
        status = ExchangeStatus.PENDING,
        durationMinutes = 60,
        meetingMode = MeetingMode.ONLINE,
        scheduledAt = "2026-10-15T10:00:00Z",
        createdAt = "2026-10-05T00:00:00Z",
        updatedAt = "2026-10-05T00:00:00Z"
    )

    private val mockExchangeRepo = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> = error("Not used")
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> = Result.success(currentExchange)

        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> {
            currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
            return Result.success(currentExchange)
        }

        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> {
            currentExchange = currentExchange.copy(status = ExchangeStatus.REJECTED)
            return Result.success(currentExchange)
        }

        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> {
            currentExchange = currentExchange.copy(status = ExchangeStatus.CANCELLED, cancellationReason = reason)
            return Result.success(currentExchange)
        }

        override suspend fun completeRequest(id: String): Result<ExchangeRequest> {
            currentExchange = currentExchange.copy(receiverCompletedAt = "2026-10-15T11:00:00Z")
            return Result.success(currentExchange)
        }
    }

    private var mockHasRated = false
    private val mockRatingRepo = object : com.skillexchange.app.domain.repository.IRatingRepository {
        override suspend fun createRating(exchangeId: String, score: Int, comment: String?): Result<com.skillexchange.app.domain.model.rating.Rating> = error("Not used")
        override suspend fun getUserRatings(userId: String): Result<List<com.skillexchange.app.domain.model.rating.Rating>> = Result.success(emptyList())
        override suspend fun getUserReputation(userId: String): Result<com.skillexchange.app.domain.model.rating.Reputation> = error("Not used")
        override suspend fun getMyExchangeRating(exchangeId: String): Result<com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus> =
            Result.success(com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus(hasRated = mockHasRated, rating = null))
    }

    private var currentProposal: RescheduleProposal? = null
    private val mockRescheduleRepo = object : IRescheduleRepository {
        override suspend fun createProposal(exchangeId: String, dto: CreateRescheduleProposalDto): Result<RescheduleProposal> {
            val now = java.time.Instant.now().toString()
            val proposal = RescheduleProposal(
                id = "prop_1",
                exchangeId = exchangeId,
                proposerId = tokenManager.getUserId() ?: "user_sender",
                newScheduledAt = dto.newScheduledAt,
                reason = dto.reason,
                status = RescheduleStatus.PENDING,
                proposalNumber = (currentProposal?.proposalNumber ?: 0) + 1,
                respondedAt = null,
                createdAt = now,
                updatedAt = now
            )
            currentProposal = proposal
            return Result.success(proposal)
        }

        override suspend fun getCurrentProposal(exchangeId: String): Result<RescheduleProposal?> {
            return Result.success(currentProposal)
        }

        override suspend fun acceptProposal(proposalId: String): Result<RescheduleProposal> {
            val accepted = currentProposal!!.copy(status = RescheduleStatus.ACCEPTED, respondedAt = "2026-11-05T11:00:00Z")
            currentProposal = accepted
            return Result.success(accepted)
        }

        override suspend fun rejectProposal(proposalId: String): Result<RescheduleProposal> {
            val rejected = currentProposal!!.copy(status = RescheduleStatus.REJECTED, respondedAt = "2026-11-05T11:00:00Z")
            currentProposal = rejected
            return Result.success(rejected)
        }

        override suspend fun cancelProposal(proposalId: String): Result<RescheduleProposal> {
            val cancelled = currentProposal!!.copy(status = RescheduleStatus.CANCELLED)
            currentProposal = cancelled
            return Result.success(cancelled)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockHasRated = false
        currentProposal = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDetail sets exchange and determines receiver role correctly`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.exchange)
        assertEquals("ex_999", state.exchange?.id)
        assertTrue(state.isReceiver)
        assertFalse(state.isSender)
        assertEquals(ExchangeStatus.PENDING, state.status)
        assertEquals("Sender Guy", state.otherPartyName)
    }

    @Test
    fun `receiver acceptRequest updates status in-place to ACCEPTED`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        var effectReceived: BookingDetailEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(BookingDetailIntent.AcceptRequest)
        advanceUntilIdle()

        assertEquals(ExchangeStatus.ACCEPTED, viewModel.state.value.status)
        assertTrue(effectReceived is BookingDetailEffect.ShowSnackbar)
        job.cancel()
    }

    @Test
    fun `receiver rejectRequest updates status in-place to REJECTED`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.RejectRequest)
        advanceUntilIdle()

        assertEquals(ExchangeStatus.REJECTED, viewModel.state.value.status)
    }

    @Test
    fun `cancel dialog updates reason and confirm cancel updates status to CANCELLED`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.OpenCancelDialog)
        assertTrue(viewModel.state.value.showCancelDialog)

        viewModel.onIntent(BookingDetailIntent.UpdateCancelReason("Bận việc đột xuất"))
        assertEquals("Bận việc đột xuất", viewModel.state.value.cancelReason)

        viewModel.onIntent(BookingDetailIntent.ConfirmCancel)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showCancelDialog)
        assertEquals(ExchangeStatus.CANCELLED, viewModel.state.value.status)
        assertEquals("Bận việc đột xuất", viewModel.state.value.exchange?.cancellationReason)
    }

    @Test
    fun `confirmCompletion updates receiverCompletedAt and waiting state`() = runTest {
        // Set exchange to ACCEPTED first
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.ConfirmCompletion)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasUserConfirmed)
        assertTrue(state.isWaitingForOtherToConfirm)
        assertNotNull(state.exchange?.receiverCompletedAt)
    }

    @Test
    fun `completed exchange checks and reflects hasRated status`() = runTest {
        currentExchange = currentExchange.copy(
            status = ExchangeStatus.COMPLETED,
            senderCompletedAt = "2026-10-15T11:00:00Z",
            receiverCompletedAt = "2026-10-15T11:00:00Z"
        )
        mockHasRated = true

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ExchangeStatus.COMPLETED, state.status)
        assertTrue(state.hasRated)
    }

    @Test
    fun `completed exchange allows rebooking partner whether user is sender or receiver`() = runTest {
        currentExchange = currentExchange.copy(
            status = ExchangeStatus.COMPLETED,
            senderId = "user_sender",
            receiverId = "user_receiver",
            senderCompletedAt = "2026-10-15T11:00:00Z",
            receiverCompletedAt = "2026-10-15T11:00:00Z"
        )
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ExchangeStatus.COMPLETED, state.status)
        assertTrue(state.isReceiver)
        val partnerId = if (state.isSender) state.exchange?.receiverId else state.exchange?.senderId
        assertEquals("user_sender", partnerId)
    }

    @Test
    fun `loadDetail on ACCEPTED loads current proposal and updates state`() = runTest {
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
        val now = java.time.Instant.now()
        currentProposal = RescheduleProposal(
            id = "prop_1",
            exchangeId = "ex_999",
            proposerId = "user_sender",
            newScheduledAt = now.plus(java.time.Duration.ofDays(3)).toString(),
            reason = "Bận việc",
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.rescheduleProposal)
        assertEquals("prop_1", state.rescheduleProposal?.id)
        assertTrue(state.isReschedulePending)
        assertFalse(state.isRescheduleProposer)
    }

    @Test
    fun `reschedule dialog open and dismiss toggle showRescheduleDialog`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.OpenRescheduleDialog)
        assertTrue(viewModel.state.value.showRescheduleDialog)

        viewModel.onIntent(BookingDetailIntent.DismissRescheduleDialog)
        assertFalse(viewModel.state.value.showRescheduleDialog)
    }

    @Test
    fun `submitReschedule creates proposal and dismisses dialog`() = runTest {
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.OpenRescheduleDialog)
        assertTrue(viewModel.state.value.showRescheduleDialog)

        var effectReceived: BookingDetailEffect? = null
        val job = launch { effectReceived = viewModel.effect.first() }

        viewModel.onIntent(BookingDetailIntent.SubmitReschedule(
            newScheduledAt = "2026-11-10T14:00:00Z",
            reason = "Dời lịch chiều nhé"
        ))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showRescheduleDialog)
        assertNotNull(state.rescheduleProposal)
        assertEquals("2026-11-10T14:00:00Z", state.rescheduleProposal?.newScheduledAt)
        assertEquals("Dời lịch chiều nhé", state.rescheduleProposal?.reason)
        assertTrue(effectReceived is BookingDetailEffect.ShowSnackbar)
        job.cancel()
    }

    @Test
    fun `acceptRescheduleProposal updates proposal, exchange scheduledAt and triggers banner`() = runTest {
        val now = java.time.Instant.now()
        val futureTime = now.plus(java.time.Duration.ofDays(3)).toString()
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED, scheduledAt = "2026-10-15T10:00:00Z")
        currentProposal = RescheduleProposal(
            id = "prop_1",
            exchangeId = "ex_999",
            proposerId = "user_sender",
            newScheduledAt = futureTime,
            reason = "Bận việc",
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.AcceptRescheduleProposal)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(RescheduleStatus.ACCEPTED, state.rescheduleProposal?.status)
        assertEquals(futureTime, state.exchange?.scheduledAt)
        assertTrue(state.showRescheduleSuccessBanner)

        viewModel.onIntent(BookingDetailIntent.DismissRescheduleSuccessBanner)
        assertFalse(viewModel.state.value.showRescheduleSuccessBanner)
    }

    @Test
    fun `rejectRescheduleProposal updates proposal status in state`() = runTest {
        val now = java.time.Instant.now()
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
        currentProposal = RescheduleProposal(
            id = "prop_1",
            exchangeId = "ex_999",
            proposerId = "user_sender",
            newScheduledAt = now.plus(java.time.Duration.ofDays(3)).toString(),
            reason = "Bận việc",
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.RejectRescheduleProposal)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(RescheduleStatus.REJECTED, state.rescheduleProposal?.status)
    }

    @Test
    fun `cancelRescheduleProposal updates proposal status in state`() = runTest {
        val now = java.time.Instant.now()
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
        currentProposal = RescheduleProposal(
            id = "prop_1",
            exchangeId = "ex_999",
            proposerId = "user_receiver",
            newScheduledAt = now.plus(java.time.Duration.ofDays(3)).toString(),
            reason = "Tôi bận",
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.CancelRescheduleProposal)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(RescheduleStatus.CANCELLED, state.rescheduleProposal?.status)
    }

    @Test
    fun `confirmCompletion blocked with snackbar when pending reschedule proposal exists`() = runTest {
        val now = java.time.Instant.now()
        currentExchange = currentExchange.copy(status = ExchangeStatus.ACCEPTED)
        currentProposal = RescheduleProposal(
            id = "prop_1",
            exchangeId = "ex_999",
            proposerId = "user_sender",
            newScheduledAt = now.plus(java.time.Duration.ofDays(3)).toString(),
            status = RescheduleStatus.PENDING,
            proposalNumber = 1,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo, mockRescheduleRepo)
        advanceUntilIdle()

        var effectReceived: BookingDetailEffect? = null
        val job = launch { effectReceived = viewModel.effect.first() }

        viewModel.onIntent(BookingDetailIntent.ConfirmCompletion)
        advanceUntilIdle()

        assertTrue(effectReceived is BookingDetailEffect.ShowSnackbar)
        assertTrue((effectReceived as BookingDetailEffect.ShowSnackbar).message.contains("đề xuất đổi lịch"))
        job.cancel()
    }
}
