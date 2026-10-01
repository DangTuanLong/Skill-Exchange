package com.skillexchange.app.presentation.profile

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
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockProfileRepository = object : IProfileRepository {
        var profile = Profile(id = "p1", userId = "u1", fullName = "Nguyễn Văn A", bio = "Dev", city = "Hà Nội")

        override suspend fun getMyProfile(): Result<Profile> = Result.success(profile)

        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?
        ): Result<Profile> {
            profile = profile.copy(fullName = fullName, bio = bio, city = city, avatarUrl = avatarUrl)
            return Result.success(profile)
        }
    }

    private val mockSkillRepository = object : ISkillRepository {
        val skillsList = mutableListOf<UserSkill>()

        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(
            listOf(SkillCategory(id = 1, name = "Lập trình", icon = "💻"))
        )

        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> = Result.success(skillsList)

        override suspend fun addUserSkill(
            skillId: Int, type: String, proficiencyLevel: Int, note: String?
        ): Result<UserSkill> {
            val sk = UserSkill(
                id = "us_${skillsList.size + 1}",
                skillId = skillId,
                skillName = "Kotlin",
                categoryName = "Lập trình",
                type = if (type == "HAVE") SkillType.HAVE else SkillType.WANT,
                proficiencyLevel = proficiencyLevel
            )
            skillsList.add(sk)
            return Result.success(sk)
        }

        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> {
            skillsList.removeAll { it.id == userSkillId }
            return Result.success(true)
        }
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
    fun `loadData updates profile state with user profile and categories`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Nguyễn Văn A", state.fullName)
        assertEquals("Dev", state.bio)
        assertEquals("Hà Nội", state.city)
        assertEquals(1, state.categories.size)
    }

    @Test
    fun `FullNameChanged intent updates state full name and clears error`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.FullNameChanged("Trần Thị B"))

        assertEquals("Trần Thị B", viewModel.state.value.fullName)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `SaveProfile intent with blank name sets error`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.FullNameChanged("   "))
        viewModel.onIntent(ProfileIntent.SaveProfile)

        assertNotNull(viewModel.state.value.error)
        assertEquals("Tên không được để trống", viewModel.state.value.error)
    }

    @Test
    fun `SaveProfile intent with valid data updates profile successfully`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.FullNameChanged("Lê Văn C"))
        viewModel.onIntent(ProfileIntent.BioChanged("Fullstack Dev"))
        viewModel.onIntent(ProfileIntent.CityChanged("TP.HCM"))
        viewModel.onIntent(ProfileIntent.SaveProfile)

        advanceUntilIdle()

        val updated = viewModel.state.value.savedProfile
        assertNotNull(updated)
        assertEquals("Lê Văn C", updated?.fullName)
        assertEquals("TP.HCM", updated?.city)
    }

    @Test
    fun `AddSkill intent adds skill to mySkills state`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.SetProficiency(4))
        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))

        advanceUntilIdle()

        val skills = viewModel.state.value.mySkills
        assertEquals(1, skills.size)
        assertEquals(10, skills.first().skillId)
        assertEquals(SkillType.HAVE, skills.first().type)
        assertEquals(4, skills.first().proficiencyLevel)
    }

    @Test
    fun `RemoveSkill intent removes skill from mySkills state`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))
        advanceUntilIdle()

        val skillIdToRemove = viewModel.state.value.mySkills.first().id
        viewModel.onIntent(ProfileIntent.RemoveSkill(userSkillId = skillIdToRemove))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.mySkills.isEmpty())
    }

    @Test
    fun `SelectTab and SetProficiency intents update UI state`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.SelectTab(1)) // WANT
        viewModel.onIntent(ProfileIntent.SetProficiency(5))

        assertEquals(1, viewModel.state.value.selectedTab)
        assertEquals(5, viewModel.state.value.proficiencyLevel)
    }
}
