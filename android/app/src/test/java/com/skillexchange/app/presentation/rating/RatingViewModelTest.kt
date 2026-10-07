package com.skillexchange.app.presentation.rating

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.model.rating.Rating
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IRatingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RatingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_sender") }

    private var createRatingResult: Result<Rating> = Result.success(
        Rating(
            id = "rat_123",
            exchangeId = "ex_100",
            reviewerId = "user_sender",
            reviewerName = "Sender Guy",
            reviewerAvatarUrl = null,
            revieweeId = "user_receiver",
            score = 5,
            comment = "Buổi trao đổi rất hữu ích!",
            createdAt = "2026-10-07T12:00:00Z"
        )
    )

    private val mockExchange = ExchangeRequest(
        id = "ex_100",
        senderId = "user_sender",
        receiverId = "user_receiver",
        senderName = "Sender Guy",
        receiverName = "Receiver Girl",
        skillOfferedId = 1,
        skillOfferedName = "Kotlin",
        skillWantedId = 2,
        skillWantedName = "Design",
        status = ExchangeStatus.COMPLETED,
        durationMinutes = 60,
        meetingMode = MeetingMode.ONLINE,
        scheduledAt = "2026-10-15T10:00:00Z",
        createdAt = "2026-10-05T00:00:00Z",
        updatedAt = "2026-10-05T00:00:00Z"
    )

    private val mockRatingRepository = object : IRatingRepository {
        override suspend fun createRating(exchangeId: String, score: Int, comment: String?): Result<Rating> {
            return createRatingResult
        }

        override suspend fun getUserRatings(userId: String): Result<List<Rating>> = Result.success(emptyList())
        override suspend fun getUserReputation(userId: String): Result<com.skillexchange.app.domain.model.rating.Reputation> = error("Not used")
        override suspend fun getMyExchangeRating(exchangeId: String): Result<com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus> = error("Not used")
    }

    private val mockExchangeRepository = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto): Result<ExchangeRequest> = error("Not used")
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> = Result.success(mockExchange)
        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> = error("Not used")
        override suspend fun completeRequest(id: String): Result<ExchangeRequest> = error("Not used")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has score 5 and Tuyet voi label and blank comment`() {
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)

        val state = viewModel.state.value
        assertEquals(5, state.score)
        assertEquals("Tuyệt vời!", state.scoreLabel)
        assertEquals("", state.comment)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `LoadExchange intent loads partner info and exchange title`() = runTest {
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)

        viewModel.onIntent(RatingIntent.LoadExchange("ex_100"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("ex_100", state.exchangeId)
        assertEquals("Receiver Girl", state.otherUserName)
        assertEquals("Bạn dạy Kotlin ↔ Học Design", state.exchangeTitle)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `SelectScore updates score and corresponding Vietnamese label`() {
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)

        viewModel.onIntent(RatingIntent.SelectScore(1))
        assertEquals(1, viewModel.state.value.score)
        assertEquals("Tệ", viewModel.state.value.scoreLabel)

        viewModel.onIntent(RatingIntent.SelectScore(2))
        assertEquals(2, viewModel.state.value.score)
        assertEquals("Không tốt", viewModel.state.value.scoreLabel)

        viewModel.onIntent(RatingIntent.SelectScore(3))
        assertEquals(3, viewModel.state.value.score)
        assertEquals("Bình thường", viewModel.state.value.scoreLabel)

        viewModel.onIntent(RatingIntent.SelectScore(4))
        assertEquals(4, viewModel.state.value.score)
        assertEquals("Tốt", viewModel.state.value.scoreLabel)

        viewModel.onIntent(RatingIntent.SelectScore(5))
        assertEquals(5, viewModel.state.value.score)
        assertEquals("Tuyệt vời!", viewModel.state.value.scoreLabel)
    }

    @Test
    fun `UpdateComment caps comment at 200 characters`() {
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)

        val validComment = "Bài học rất chi tiết và dễ hiểu."
        viewModel.onIntent(RatingIntent.UpdateComment(validComment))
        assertEquals(validComment, viewModel.state.value.comment)

        val longComment = "a".repeat(250)
        viewModel.onIntent(RatingIntent.UpdateComment(longComment))
        // Shouldn't accept comment longer than 200 chars
        assertEquals(validComment, viewModel.state.value.comment)
    }

    @Test
    fun `SubmitRating success emits ShowSnackbar and NavigateBack effect`() = runTest {
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)
        viewModel.onIntent(RatingIntent.LoadExchange("ex_100"))
        advanceUntilIdle()

        val effects = mutableListOf<RatingEffect>()
        val job = launch {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(RatingIntent.SubmitRating)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSubmitting)
        assertTrue(viewModel.state.value.isSuccess)
        assertTrue(effects.any { it is RatingEffect.ShowSnackbar && it.message == "Cảm ơn bạn đã gửi đánh giá!" })
        assertTrue(effects.any { it is RatingEffect.NavigateBack })
        job.cancel()
    }

    @Test
    fun `SubmitRating failure sends ShowSnackbar with error message and resets isSubmitting`() = runTest {
        createRatingResult = Result.failure(Exception("Bạn đã đánh giá buổi trao đổi này rồi"))
        val viewModel = RatingViewModel(mockRatingRepository, mockExchangeRepository, tokenManager)
        viewModel.onIntent(RatingIntent.LoadExchange("ex_100"))
        advanceUntilIdle()

        val effects = mutableListOf<RatingEffect>()
        val job = launch {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(RatingIntent.SubmitRating)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSubmitting)
        assertFalse(viewModel.state.value.isSuccess)
        assertTrue(effects.any { it is RatingEffect.ShowSnackbar && it.message.contains("đã đánh giá") })
        assertFalse(effects.any { it is RatingEffect.NavigateBack })
        job.cancel()
    }
}
