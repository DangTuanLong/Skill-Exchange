package com.skillexchange.app.presentation.profile

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("my_user_id") }

    private val mockProfileRepository = object : IProfileRepository {
        override suspend fun getMyProfile(): Result<Profile> = error("Not used")

        override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(
            Profile(id = "p_test", userId = userId, fullName = "Nguyễn Văn Detail", bio = "Hi there", city = "Đà Nẵng")
        )

        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?,
            availability: List<com.skillexchange.app.domain.model.AvailabilityWindow>?
        ): Result<Profile> = error("Not used")

        override suspend fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = error("Not used")
    }

    private val mockSkillRepository = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())

        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> {
            return if (userId == "me" || userId == "my_user_id") {
                Result.success(
                    listOf(
                        UserSkill(id = "my1", skillId = 1, skillName = "Python", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 4),
                        UserSkill(id = "my2", skillId = 2, skillName = "Tiếng Anh", categoryName = "Ngoại ngữ", type = SkillType.WANT, proficiencyLevel = 2)
                    )
                )
            } else {
                Result.success(
                    listOf(
                        UserSkill(id = "other1", skillId = 1, skillName = "Python", categoryName = "Lập trình", type = SkillType.WANT, proficiencyLevel = 3),
                        UserSkill(id = "other2", skillId = 2, skillName = "Tiếng Anh", categoryName = "Ngoại ngữ", type = SkillType.HAVE, proficiencyLevel = 4)
                    )
                )
            }
        }

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
    fun `initialization with userId loads target user profile and skills and checks valid pairs`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to "target_user_123"))
        val viewModel = ProfileDetailViewModel(savedStateHandle, mockProfileRepository, mockSkillRepository, tokenManager)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.profile)
        assertEquals("target_user_123", state.profile?.userId)
        assertEquals("Nguyễn Văn Detail", state.profile?.fullName)
        assertEquals("Đà Nẵng", state.profile?.city)
        assertEquals(2, state.skills.size)
        assertTrue("Cặp kỹ năng phải hợp lệ", state.hasValidPair)
        assertNull(state.noValidPairReason)
    }

    @Test
    fun `own profile disables exchange and sets own profile reason`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to "my_user_id"))
        val viewModel = ProfileDetailViewModel(savedStateHandle, mockProfileRepository, mockSkillRepository, tokenManager)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isOwnProfile)
        assertFalse(state.hasValidPair)
        assertEquals("Đây là hồ sơ cá nhân của bạn", state.noValidPairReason)
    }

    @Test
    fun `RequestExchange intent sends NavigateToBooking effect`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("userId" to "target_user_123"))
        val viewModel = ProfileDetailViewModel(savedStateHandle, mockProfileRepository, mockSkillRepository, tokenManager)

        advanceUntilIdle()

        var effectReceived: ProfileDetailEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(ProfileDetailIntent.RequestExchange)
        advanceUntilIdle()

        assertTrue(effectReceived is ProfileDetailEffect.NavigateToBooking)
        assertEquals("target_user_123", (effectReceived as ProfileDetailEffect.NavigateToBooking).receiverId)
        job.cancel()
    }
}
