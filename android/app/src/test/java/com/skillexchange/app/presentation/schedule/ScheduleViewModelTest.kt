package com.skillexchange.app.presentation.schedule

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IProfileRepository
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_me") }

    private var incomingResult: Result<List<ExchangeRequest>> = Result.success(emptyList())
    private var outgoingResult: Result<List<ExchangeRequest>> = Result.success(emptyList())
    private var profileResult: Result<Profile> = Result.success(
        Profile(
            userId = "user_me",
            fullName = "Nguyễn Văn Test",
            availability = listOf(
                AvailabilityWindow(day = "MON", from = "18:00", to = "21:00"),
                AvailabilityWindow(day = "WED", from = "19:00", to = "21:00"),
                AvailabilityWindow(day = "SAT", from = "09:00", to = "12:00")
            )
        )
    )

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

    private val mockProfileRepo = object : IProfileRepository {
        override suspend fun getMyProfile(): Result<Profile> = profileResult
        override suspend fun getUserProfile(userId: String): Result<Profile> = error("Not used")
        override suspend fun updateProfile(
            fullName: String,
            bio: String?,
            city: String?,
            avatarUrl: String?,
            availability: List<AvailabilityWindow>?
        ): Result<Profile> = error("Not used")
        override suspend fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = error("Not used")
    }

    private fun isoAt(date: LocalDate, hour: Int, minute: Int = 0): String =
        date.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toString()

    private fun sampleExchange(
        id: String,
        senderId: String,
        receiverId: String,
        status: ExchangeStatus,
        scheduledAt: String = "2026-10-28T10:00:00Z",
        durationMinutes: Int = 60
    ) = ExchangeRequest(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        skillOfferedId = 1,
        skillOfferedName = "Kotlin",
        skillWantedId = 2,
        skillWantedName = "English",
        status = status,
        durationMinutes = durationMinutes,
        meetingMode = MeetingMode.ONLINE,
        scheduledAt = scheduledAt,
        createdAt = "2026-10-20T00:00:00Z",
        updatedAt = "2026-10-20T00:00:00Z"
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
    fun `init loads incoming and outgoing, filters ACCEPTED only, and loads availability`() = runTest {
        val d1 = LocalDate.of(2026, 10, 28)
        val d2 = LocalDate.of(2026, 10, 29)
        val ex1 = sampleExchange("ex1", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(d1, 19))
        val ex2 = sampleExchange("ex2", "user_me", "user_partner", ExchangeStatus.ACCEPTED, isoAt(d2, 14))
        val exPending = sampleExchange("ex3", "user_other2", "user_me", ExchangeStatus.PENDING, isoAt(d1, 10))
        val exRejected = sampleExchange("ex4", "user_other3", "user_me", ExchangeStatus.REJECTED, isoAt(d2, 10))

        incomingResult = Result.success(listOf(ex1, exPending, exRejected))
        outgoingResult = Result.success(listOf(ex2))

        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.confirmedExchanges.size)
        assertTrue(state.confirmedExchanges.any { it.id == "ex1" })
        assertTrue(state.confirmedExchanges.any { it.id == "ex2" })
        assertFalse(state.confirmedExchanges.any { it.id == "ex3" })
        assertEquals(3, state.myAvailability.size)
    }

    @Test
    fun `datesWithBookings correctly computes set of LocalDates having confirmed sessions`() = runTest {
        val d1 = LocalDate.of(2026, 10, 28)
        val d2 = LocalDate.of(2026, 10, 30)
        val ex1 = sampleExchange("ex1", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(d1, 10))
        val ex2 = sampleExchange("ex2", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(d1, 18))
        val ex3 = sampleExchange("ex3", "user_me", "user_other", ExchangeStatus.ACCEPTED, isoAt(d2, 20))

        incomingResult = Result.success(listOf(ex1, ex2))
        outgoingResult = Result.success(listOf(ex3))

        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        val dates = state.datesWithBookings
        assertEquals(2, dates.size)
        assertTrue(dates.contains(d1))
        assertTrue(dates.contains(d2))
    }

    @Test
    fun `sessionsOnSelectedDate filters sessions for the selected date sorted by time`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 28)
        val otherDate = LocalDate.of(2026, 10, 29)
        val exLate = sampleExchange("ex2", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(targetDate, 19))
        val exEarly = sampleExchange("ex1", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(targetDate, 9))
        val exOtherDay = sampleExchange("ex3", "user_other", "user_me", ExchangeStatus.ACCEPTED, isoAt(otherDate, 10))

        incomingResult = Result.success(listOf(exLate, exEarly, exOtherDay))
        outgoingResult = Result.success(emptyList())

        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        viewModel.onIntent(ScheduleIntent.SelectDate(targetDate))

        val state = viewModel.state.value
        assertEquals(targetDate, state.selectedDate)
        val sessions = state.sessionsOnSelectedDate
        assertEquals(2, sessions.size)
        assertEquals("ex1", sessions[0].id)
        assertEquals("ex2", sessions[1].id)
    }

    @Test
    fun `openAvailabilityOnSelectedDate filters by day of week`() = runTest {
        // 2026-10-28 is a Wednesday (WED)
        val wednesday = LocalDate.of(2026, 10, 28)
        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        viewModel.onIntent(ScheduleIntent.SelectDate(wednesday))

        val state = viewModel.state.value
        val openWindows = state.openAvailabilityOnSelectedDate
        assertEquals(1, openWindows.size)
        assertEquals("WED", openWindows[0].day)
        assertEquals("19:00", openWindows[0].from)
    }

    @Test
    fun `selectDate updates selectedDate and currentWeekStart to Monday`() = runTest {
        val targetDate = LocalDate.of(2026, 11, 4) // Wednesday
        val expectedMonday = LocalDate.of(2026, 11, 2)

        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        viewModel.onIntent(ScheduleIntent.SelectDate(targetDate))

        val state = viewModel.state.value
        assertEquals(targetDate, state.selectedDate)
        assertEquals(expectedMonday, state.currentWeekStart)
        assertEquals(7, state.daysInCurrentWeek.size)
        assertEquals(expectedMonday, state.daysInCurrentWeek.first())
        assertEquals(LocalDate.of(2026, 11, 8), state.daysInCurrentWeek.last())
    }

    @Test
    fun `changeWeek shifts week start and selected date by offset`() = runTest {
        val initialMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        // Move 1 week forward
        viewModel.onIntent(ScheduleIntent.ChangeWeek(1))
        var state = viewModel.state.value
        assertEquals(initialMonday.plusWeeks(1), state.currentWeekStart)

        // Move 2 weeks back
        viewModel.onIntent(ScheduleIntent.ChangeWeek(-2))
        state = viewModel.state.value
        assertEquals(initialMonday.minusWeeks(1), state.currentWeekStart)
    }

    @Test
    fun `jumpToToday resets selectedDate to today`() = runTest {
        val futureDate = LocalDate.now().plusMonths(2)
        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        viewModel.onIntent(ScheduleIntent.SelectDate(futureDate))
        assertEquals(futureDate, viewModel.state.value.selectedDate)

        viewModel.onIntent(ScheduleIntent.JumpToToday)
        assertEquals(LocalDate.now(), viewModel.state.value.selectedDate)
        assertEquals(
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            viewModel.state.value.currentWeekStart
        )
    }

    @Test
    fun `network failure on both requests sets error state`() = runTest {
        incomingResult = Result.failure(RuntimeException("Mất kết nối mạng"))
        outgoingResult = Result.failure(RuntimeException("Mất kết nối mạng"))

        val viewModel = ScheduleViewModel(mockExchangeRepo, mockProfileRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.confirmedExchanges.isEmpty())
    }
}
