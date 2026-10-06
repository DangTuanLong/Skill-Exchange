package com.skillexchange.app.presentation.booking

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import com.skillexchange.app.presentation.booking.request.BookingRequestEffect
import com.skillexchange.app.presentation.booking.request.BookingRequestIntent
import com.skillexchange.app.presentation.booking.request.BookingRequestViewModel
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
class BookingRequestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockProfileRepo = object : IProfileRepository {
        override suspend fun getMyProfile(): Result<Profile> = error("Not used")
        override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(
            Profile(id = "p2", userId = userId, fullName = "Trần Thị B", city = "Hà Nội")
        )
        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?,
            availability: List<com.skillexchange.app.domain.model.AvailabilityWindow>?
        ): Result<Profile> = error("Not used")
        override suspend fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = error("Not used")
    }

    private var mySkillsList: List<UserSkill> = emptyList()
    private var receiverSkillsList: List<UserSkill> = emptyList()

    private val mockSkillRepo = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())
        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> {
            return if (userId == "me") Result.success(mySkillsList)
            else Result.success(receiverSkillsList)
        }
        override suspend fun addUserSkill(skillId: Int, type: String, proficiencyLevel: Int, note: String?): Result<UserSkill> = error("Not used")
        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = error("Not used")
    }

    private var lastCreatedDto: CreateExchangeRequestDto? = null
    private var createResult: Result<ExchangeRequest> = Result.success(
        ExchangeRequest(
            id = "ex_123",
            senderId = "me_user",
            receiverId = "user_b",
            skillOfferedId = 1,
            skillOfferedName = "Python",
            skillWantedId = 2,
            skillWantedName = "Guitar",
            status = ExchangeStatus.PENDING,
            durationMinutes = 60,
            meetingMode = MeetingMode.UNDECIDED,
            scheduledAt = "2026-10-10T10:00:00Z",
            createdAt = "2026-10-05T00:00:00Z",
            updatedAt = "2026-10-05T00:00:00Z"
        )
    )

    private val mockExchangeRepo = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> {
            lastCreatedDto = dto
            return createResult
        }
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> = error("Not used")
        override suspend fun completeRequest(id: String): Result<ExchangeRequest> = error("Not used")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mySkillsList = listOf(
            UserSkill(id = "1", skillId = 1, skillName = "Python", type = SkillType.HAVE, proficiencyLevel = 4),
            UserSkill(id = "2", skillId = 2, skillName = "Guitar", type = SkillType.WANT, proficiencyLevel = 2)
        )
        receiverSkillsList = listOf(
            UserSkill(id = "3", skillId = 1, skillName = "Python", type = SkillType.WANT, proficiencyLevel = 3),
            UserSkill(id = "4", skillId = 2, skillName = "Guitar", type = SkillType.HAVE, proficiencyLevel = 3)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData detects valid pairs and preselects skills`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("receiverId" to "user_b"))
        val viewModel = BookingRequestViewModel(savedStateHandle, mockProfileRepo, mockSkillRepo, mockExchangeRepo)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.hasValidPair)
        assertEquals(1, state.validTeachSkills.size)
        assertEquals(1, state.validLearnSkills.size)
        assertEquals(1, state.selectedSkillOfferedId)
        assertEquals(2, state.selectedSkillWantedId)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `loadData detects when no valid pair exists`() = runTest {
        // Receiver does not want Python (level too high required)
        receiverSkillsList = listOf(
            UserSkill(id = "3", skillId = 1, skillName = "Python", type = SkillType.WANT, proficiencyLevel = 5),
            UserSkill(id = "4", skillId = 2, skillName = "Guitar", type = SkillType.HAVE, proficiencyLevel = 3)
        )
        val savedStateHandle = SavedStateHandle(mapOf("receiverId" to "user_b"))
        val viewModel = BookingRequestViewModel(savedStateHandle, mockProfileRepo, mockSkillRepo, mockExchangeRepo)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasValidPair)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `submitRequest with past date fails future validation`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("receiverId" to "user_b"))
        val viewModel = BookingRequestViewModel(savedStateHandle, mockProfileRepo, mockSkillRepo, mockExchangeRepo)

        advanceUntilIdle()

        // Set date to yesterday
        viewModel.onIntent(BookingRequestIntent.SetScheduledDate(System.currentTimeMillis() - 86400000L))

        var effectReceived: BookingRequestEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(BookingRequestIntent.SubmitRequest)
        advanceUntilIdle()

        assertTrue(effectReceived is BookingRequestEffect.ShowSnackbar)
        assertEquals("Thời gian trao đổi phải ở tương lai", (effectReceived as BookingRequestEffect.ShowSnackbar).message)
        assertNull(lastCreatedDto)
        job.cancel()
    }

    @Test
    fun `submitRequest success sends NavigateToDetail effect`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("receiverId" to "user_b"))
        val viewModel = BookingRequestViewModel(savedStateHandle, mockProfileRepo, mockSkillRepo, mockExchangeRepo)

        advanceUntilIdle()

        // Set date to 2 days ahead
        viewModel.onIntent(BookingRequestIntent.SetScheduledDate(System.currentTimeMillis() + 172800000L))
        viewModel.onIntent(BookingRequestIntent.SetDuration(90))
        viewModel.onIntent(BookingRequestIntent.SetMeetingMode(MeetingMode.ONLINE))
        viewModel.onIntent(BookingRequestIntent.SetMessage("Hello let's meet!"))

        var effectReceived: BookingRequestEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(BookingRequestIntent.SubmitRequest)
        advanceUntilIdle()

        assertNotNull(lastCreatedDto)
        assertEquals("user_b", lastCreatedDto?.receiverId)
        assertEquals(1, lastCreatedDto?.skillOfferedId)
        assertEquals(2, lastCreatedDto?.skillWantedId)
        assertEquals(90, lastCreatedDto?.durationMinutes)
        assertEquals("ONLINE", lastCreatedDto?.meetingMode)
        assertEquals("Hello let's meet!", lastCreatedDto?.message)

        assertTrue(effectReceived is BookingRequestEffect.NavigateToDetail)
        val navEffect = effectReceived as BookingRequestEffect.NavigateToDetail
        assertEquals("ex_123", navEffect.exchangeId)
        assertEquals("user_b", navEffect.receiverId)
        job.cancel()
    }
}
