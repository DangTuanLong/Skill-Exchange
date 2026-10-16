package com.skillexchange.app.presentation.dashboard

import com.skillexchange.app.data.remote.stats.*
import com.skillexchange.app.domain.repository.IStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleStats = UserStatsDto(
        totalSessions = 8,
        avgRating = 4.9,
        ratingCount = 7,
        completionRate = 0.95,
        monthlySessions = listOf(
            MonthlySessionDto("2026-05", "05/2026", 3),
            MonthlySessionDto("2026-04", "04/2026", 5)
        ),
        timeline = listOf(
            TimelineItemDto("t1", "EXCHANGE", "Hoàn thành buổi trao đổi", "Với Nguyễn A", "2026-05-12T10:00:00")
        ),
        radarScores = listOf(
            RadarAxisDto(1, "Lập trình", 4.0, 2),
            RadarAxisDto(2, "Ngoại ngữ", 5.0, 1)
        ),
        skillGapSuggestions = listOf(
            SkillGapSuggestionDto(10, "Figma", "Thiết kế", 3, "Có 3 người cần bạn")
        )
    )

    private val sampleLeaderboard = listOf(
        LeaderboardItemDto(
            rank = 1,
            userId = "u-top-1",
            fullName = "Trần Văn Top",
            city = "Hà Nội",
            score = 4.95,
            badge = "Xuất sắc",
            totalExchanges = 25,
            ratingCount = 20
        )
    )

    private val mockStatsRepository = object : IStatsRepository {
        var statsResult: Result<UserStatsDto> = Result.success(sampleStats)
        var leaderboardResult: Result<List<LeaderboardItemDto>> = Result.success(sampleLeaderboard)

        override suspend fun getUserStats(): Result<UserStatsDto> = statsResult
        override suspend fun getLeaderboard(): Result<List<LeaderboardItemDto>> = leaderboardResult
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockStatsRepository.statsResult = Result.success(sampleStats)
        mockStatsRepository.leaderboardResult = Result.success(sampleLeaderboard)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `LoadData success populates userStats and leaderboard`() = runTest {
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isEmpty)
        assertNotNull(state.userStats)
        assertEquals(8, state.userStats?.totalSessions)
        assertEquals(4.9, state.userStats?.avgRating)
        assertEquals(1, state.leaderboard.size)
        assertEquals("u-top-1", state.leaderboard[0].userId)
    }

    @Test
    fun `LoadData failure sets error message`() = runTest {
        mockStatsRepository.statsResult = Result.failure(Exception("Lỗi kết nối máy chủ"))
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Lỗi kết nối máy chủ", state.error)
        assertNull(state.userStats)
    }

    @Test
    fun `Empty stats marks isEmpty as true`() = runTest {
        mockStatsRepository.statsResult = Result.success(
            UserStatsDto(
                totalSessions = 0,
                avgRating = 0.0,
                ratingCount = 0,
                completionRate = 0.0,
                monthlySessions = emptyList(),
                timeline = emptyList(),
                radarScores = listOf(RadarAxisDto(1, "Lập trình", 0.0, 0)),
                skillGapSuggestions = emptyList()
            )
        )
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `Refresh updates data successfully`() = runTest {
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        // Cập nhật stats mới
        val updatedStats = sampleStats.copy(totalSessions = 10)
        mockStatsRepository.statsResult = Result.success(updatedStats)

        viewModel.onIntent(DashboardIntent.Refresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertEquals(10, state.userStats?.totalSessions)
    }

    @Test
    fun `ClickLeaderboardUser emits NavigateToProfile effect`() = runTest {
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        var effectReceived: DashboardEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(DashboardIntent.ClickLeaderboardUser("user-123"))
        advanceUntilIdle()

        assertEquals(DashboardEffect.NavigateToProfile("user-123"), effectReceived)
        job.cancel()
    }

    @Test
    fun `ClickSkillGap emits ShowSnackbar effect`() = runTest {
        val viewModel = DashboardViewModel(mockStatsRepository)
        advanceUntilIdle()

        var effectReceived: DashboardEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(DashboardIntent.ClickSkillGap(5, "Figma"))
        advanceUntilIdle()

        assertTrue(effectReceived is DashboardEffect.ShowSnackbar)
        assertTrue((effectReceived as DashboardEffect.ShowSnackbar).message.contains("Figma"))
        job.cancel()
    }
}
