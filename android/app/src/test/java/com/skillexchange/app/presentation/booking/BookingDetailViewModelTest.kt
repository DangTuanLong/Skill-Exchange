package com.skillexchange.app.presentation.booking

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.IExchangeRepository
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockHasRated = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDetail sets exchange and determines receiver role correctly`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

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
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

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
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

        advanceUntilIdle()

        viewModel.onIntent(BookingDetailIntent.RejectRequest)
        advanceUntilIdle()

        assertEquals(ExchangeStatus.REJECTED, viewModel.state.value.status)
    }

    @Test
    fun `cancel dialog updates reason and confirm cancel updates status to CANCELLED`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("exchangeId" to "ex_999"))
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

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
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

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
        val viewModel = BookingDetailViewModel(savedStateHandle, mockExchangeRepo, tokenManager, mockRatingRepo)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ExchangeStatus.COMPLETED, state.status)
        assertTrue(state.hasRated)
    }
}
