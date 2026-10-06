package com.skillexchange.app.presentation.booking

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.presentation.booking.list.BookingListIntent
import com.skillexchange.app.presentation.booking.list.BookingListViewModel
import com.skillexchange.app.presentation.booking.list.BookingTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BookingListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("my_user") }

    private var incomingResult: Result<List<ExchangeRequest>> = Result.success(emptyList())
    private var outgoingResult: Result<List<ExchangeRequest>> = Result.success(emptyList())

    private val mockExchangeRepo = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> = error("Not used")
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> = incomingResult
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> = outgoingResult
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> = error("Not used")
        override suspend fun completeRequest(id: String): Result<ExchangeRequest> = error("Not used")
    }

    private fun sampleExchange(
        id: String,
        senderId: String,
        receiverId: String,
        status: ExchangeStatus
    ) = ExchangeRequest(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        skillOfferedId = 1,
        skillOfferedName = "S1",
        skillWantedId = 2,
        skillWantedName = "S2",
        status = status,
        durationMinutes = 60,
        meetingMode = MeetingMode.UNDECIDED,
        scheduledAt = "2026-10-10T10:00:00Z",
        createdAt = "2026-10-05T00:00:00Z",
        updatedAt = "2026-10-05T00:00:00Z"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData categorizes requests into incoming, outgoing, and history tabs`() = runTest {
        val inPending = sampleExchange("in_1", "other_1", "my_user", ExchangeStatus.PENDING)
        val inAccepted = sampleExchange("in_2", "other_2", "my_user", ExchangeStatus.ACCEPTED)
        val inCompleted = sampleExchange("in_3", "other_3", "my_user", ExchangeStatus.COMPLETED)

        val outPending = sampleExchange("out_1", "my_user", "other_4", ExchangeStatus.PENDING)
        val outCancelled = sampleExchange("out_2", "my_user", "other_5", ExchangeStatus.CANCELLED)

        incomingResult = Result.success(listOf(inPending, inAccepted, inCompleted))
        outgoingResult = Result.success(listOf(outPending, outCancelled))

        val viewModel = BookingListViewModel(mockExchangeRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.incomingList.size) // in_1, in_2
        assertEquals(1, state.outgoingList.size) // out_1
        assertEquals(2, state.historyList.size)  // in_3 (COMPLETED), out_2 (CANCELLED)

        // Default tab is INCOMING
        assertEquals(BookingTab.INCOMING, state.selectedTab)
        assertEquals(2, state.currentList.size)

        // Switch to OUTGOING tab
        viewModel.onIntent(BookingListIntent.SelectTab(BookingTab.OUTGOING))
        assertEquals(BookingTab.OUTGOING, viewModel.state.value.selectedTab)
        assertEquals(1, viewModel.state.value.currentList.size)
        assertEquals("out_1", viewModel.state.value.currentList.first().id)

        // Switch to HISTORY tab
        viewModel.onIntent(BookingListIntent.SelectTab(BookingTab.HISTORY))
        assertEquals(BookingTab.HISTORY, viewModel.state.value.selectedTab)
        assertEquals(2, viewModel.state.value.currentList.size)
    }

    @Test
    fun `loadData handles error when both endpoints fail`() = runTest {
        incomingResult = Result.failure(Exception("Lỗi kết nối máy chủ"))
        outgoingResult = Result.failure(Exception("Lỗi kết nối máy chủ"))

        val viewModel = BookingListViewModel(mockExchangeRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.incomingList.isEmpty())
        assertTrue(state.outgoingList.isEmpty())
        assertTrue(state.historyList.isEmpty())
    }
}
