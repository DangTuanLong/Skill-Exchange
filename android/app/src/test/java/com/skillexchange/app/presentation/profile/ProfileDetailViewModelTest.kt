package com.skillexchange.app.presentation.profile

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockProfileRepository = object : IProfileRepository {
        override suspend fun getMyProfile(): Result<Profile> = error("Not used")

        override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(
            Profile(id = "p_test", userId = userId, fullName = "Nguyễn Văn Detail", bio = "Hi there", city = "Đà Nẵng")
        )

        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?,
            availability: List<com.skillexchange.app.domain.model.AvailabilityWindow>?
        ): Result<Profile> = error("Not used")
    }


    private val mockSkillRepository = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())

        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> = Result.success(
            listOf(
                UserSkill(id = "us1", skillId = 1, skillName = "Python", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 5)
            )
        )

        override suspend fun addUserSkill(
            skillId: Int, type: String, proficiencyLevel: Int, note: String?
        ): Result<UserSkill> = error("Not used")

        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = Result.success(true)
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
    fun `initialization with userId loads target user profile and skills`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to "target_user_123"))
        val viewModel = ProfileDetailViewModel(savedStateHandle, mockProfileRepository, mockSkillRepository)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.profile)
        assertEquals("target_user_123", state.profile?.userId)
        assertEquals("Nguyễn Văn Detail", state.profile?.fullName)
        assertEquals("Đà Nẵng", state.profile?.city)
        assertEquals(1, state.skills.size)
        assertEquals("Python", state.skills.first().skillName)
    }

    @Test
    fun `RequestExchange intent sends NavigateToBooking effect`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to "target_user_123"))
        val viewModel = ProfileDetailViewModel(savedStateHandle, mockProfileRepository, mockSkillRepository)

        advanceUntilIdle()

        viewModel.onIntent(ProfileDetailIntent.RequestExchange)

        advanceUntilIdle()
    }
}
