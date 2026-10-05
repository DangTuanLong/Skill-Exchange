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

        override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(profile)

        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?,
            availability: List<com.skillexchange.app.domain.model.AvailabilityWindow>?
        ): Result<Profile> {
            profile = profile.copy(
                fullName = fullName,
                bio = bio,
                city = city,
                avatarUrl = avatarUrl,
                availability = availability ?: profile.availability
            )
            return Result.success(profile)
        }

        var uploadAvatarResult: Result<String> = Result.success("https://r2.example.com/avatars/new_avatar.jpg")

        override suspend fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> {
            return uploadAvatarResult
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

    @Test
    fun `AddSkill intent deduplicates skill with same skillId and type`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))
        advanceUntilIdle()
        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))
        advanceUntilIdle()

        val skills = viewModel.state.value.mySkills
        assertEquals(1, skills.size)
        assertEquals(10, skills.first().skillId)
    }

    @Test
    fun `SelectSkillForEdit and SetProficiency update selected skill proficiency`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))
        advanceUntilIdle()

        val addedSkillId = viewModel.state.value.mySkills.first().id
        viewModel.onIntent(ProfileIntent.SelectSkillForEdit(addedSkillId))
        assertEquals(addedSkillId, viewModel.state.value.selectedSkillIdForEdit)

        viewModel.onIntent(ProfileIntent.SetProficiency(5))
        advanceUntilIdle()

        assertEquals(5, viewModel.state.value.mySkills.first().proficiencyLevel)
    }

    @Test
    fun `RemoveSkill clears selectedSkillIdForEdit if removed skill was selected`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.AddSkill(skillId = 10))
        advanceUntilIdle()

        val addedSkillId = viewModel.state.value.mySkills.first().id
        viewModel.onIntent(ProfileIntent.SelectSkillForEdit(addedSkillId))
        viewModel.onIntent(ProfileIntent.RemoveSkill(addedSkillId))
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedSkillIdForEdit)
    }

    @Test
    fun `AddAvailabilityWindow adds valid window to state`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val window = com.skillexchange.app.domain.model.AvailabilityWindow(day = "MON", from = "18:00", to = "21:00")
        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(window))

        assertTrue(viewModel.state.value.availability.contains(window))
    }

    @Test
    fun `AddAvailabilityWindow rejects overlapping window on same day`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val window1 = com.skillexchange.app.domain.model.AvailabilityWindow(day = "MON", from = "18:00", to = "21:00")
        val windowOverlap = com.skillexchange.app.domain.model.AvailabilityWindow(day = "MON", from = "20:00", to = "22:00")

        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(window1))
        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(windowOverlap))

        assertEquals(1, viewModel.state.value.availability.size)
        assertEquals(window1, viewModel.state.value.availability.first())
    }

    @Test
    fun `AddAvailabilityWindow rejects invalid times`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val invalidFormat = com.skillexchange.app.domain.model.AvailabilityWindow(day = "MON", from = "8:00", to = "21:00")
        val invertedTimes = com.skillexchange.app.domain.model.AvailabilityWindow(day = "MON", from = "21:00", to = "18:00")

        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(invalidFormat))
        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(invertedTimes))

        assertTrue(viewModel.state.value.availability.isEmpty())
    }

    @Test
    fun `RemoveAvailabilityWindow removes window from state`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val window = com.skillexchange.app.domain.model.AvailabilityWindow(day = "TUE", from = "14:00", to = "16:00")
        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(window))
        assertEquals(1, viewModel.state.value.availability.size)

        viewModel.onIntent(ProfileIntent.RemoveAvailabilityWindow(window))
        assertTrue(viewModel.state.value.availability.isEmpty())
    }

    @Test
    fun `SaveProfile saves availability to repository`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val window = com.skillexchange.app.domain.model.AvailabilityWindow(day = "FRI", from = "09:00", to = "11:00")
        viewModel.onIntent(ProfileIntent.FullNameChanged("Nguyễn Văn A"))
        viewModel.onIntent(ProfileIntent.AddAvailabilityWindow(window))
        viewModel.onIntent(ProfileIntent.SaveProfile)
        advanceUntilIdle()

        val saved = mockProfileRepository.profile
        assertEquals(1, saved.availability.size)
        assertEquals(window, saved.availability.first())
    }

    @Test
    fun `UploadAvatar intent with file greater than 2MB does not upload and sends snackbar`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        val largeBytes = ByteArray(2 * 1024 * 1024 + 1)
        viewModel.onIntent(ProfileIntent.UploadAvatar(largeBytes, "avatar.jpg", "image/jpeg"))
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isUploadingAvatar)
        assertEquals("", viewModel.state.value.avatarUrl)
    }

    @Test
    fun `UploadAvatar intent with valid file uploads successfully and updates avatarUrl`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        mockProfileRepository.uploadAvatarResult = Result.success("https://r2.example.com/avatars/uploaded_123.jpg")
        val validBytes = byteArrayOf(1, 2, 3)
        viewModel.onIntent(ProfileIntent.UploadAvatar(validBytes, "avatar.jpg", "image/jpeg"))
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isUploadingAvatar)
        assertEquals("https://r2.example.com/avatars/uploaded_123.jpg", viewModel.state.value.avatarUrl)
    }

    @Test
    fun `UploadAvatar intent failure resets isUploadingAvatar to false`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        mockProfileRepository.uploadAvatarResult = Result.failure(Exception("413 Payload Too Large"))
        val validBytes = byteArrayOf(1, 2, 3)
        viewModel.onIntent(ProfileIntent.UploadAvatar(validBytes, "avatar.jpg", "image/jpeg"))
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isUploadingAvatar)
    }
}

