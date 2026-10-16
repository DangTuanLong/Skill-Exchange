package com.skillexchange.app.presentation.profile

import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    @Test
    fun `RequestSkillExtraction populates extractedSkills and opens bottom sheet`() = runTest {
        val mockNlpRepo = object : com.skillexchange.app.domain.repository.INlpRepository {
            override suspend fun extractSkills(text: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.success(listOf(
                    com.skillexchange.app.data.remote.nlp.ExtractedSkillDto(
                        skillId = 1,
                        skillName = "Kotlin",
                        categoryId = 1,
                        categoryName = "Lập trình",
                        confidence = 1.0,
                        matchType = "EXACT",
                        matchedTerm = "Kotlin"
                    ),
                    com.skillexchange.app.data.remote.nlp.ExtractedSkillDto(
                        skillId = 2,
                        skillName = "Python",
                        categoryId = 1,
                        categoryName = "Lập trình",
                        confidence = 0.9,
                        matchType = "ALIAS",
                        matchedTerm = "py"
                    )
                ))
            }
            override suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.success(emptyList())
            }
        }

        val viewModel = ProfileViewModel(
            profileRepo = mockProfileRepository,
            skillRepo = mockSkillRepository,
            nlpRepo = mockNlpRepo
        )
        advanceUntilIdle()

        // Thêm trước 1 kỹ năng id=1 vào HAVE
        mockSkillRepository.skillsList.clear()
        mockSkillRepository.skillsList.add(
            UserSkill("us1", 1, "Kotlin", "Lập trình", SkillType.HAVE, 3)
        )
        viewModel.onIntent(ProfileIntent.LoadData)
        advanceUntilIdle()

        // Set bio và gọi request
        viewModel.onIntent(ProfileIntent.BioChanged("Lập trình Kotlin và py"))
        viewModel.onIntent(ProfileIntent.RequestSkillExtraction)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showSkillExtractionSheet)
        assertEquals(2, state.extractedSkills.size)

        // Skill 1 đã có -> isAlreadyAdded = true, isSelected = false
        val skill1 = state.extractedSkills.find { it.skill.skillId == 1 }!!
        assertTrue(skill1.isAlreadyAdded)
        assertFalse(skill1.isSelected)

        // Skill 2 chưa có -> isAlreadyAdded = false, isSelected = true, level = 2 mặc định
        val skill2 = state.extractedSkills.find { it.skill.skillId == 2 }!!
        assertFalse(skill2.isAlreadyAdded)
        assertTrue(skill2.isSelected)
        assertEquals(2, skill2.level)
    }

    @Test
    fun `ConfirmAddExtractedSkills adds only selected and new skills to HAVE list`() = runTest {
        val mockNlpRepo = object : com.skillexchange.app.domain.repository.INlpRepository {
            override suspend fun extractSkills(text: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.success(listOf(
                    com.skillexchange.app.data.remote.nlp.ExtractedSkillDto(
                        skillId = 5,
                        skillName = "Python",
                        categoryId = 1,
                        categoryName = "Lập trình",
                        confidence = 1.0,
                        matchType = "EXACT",
                        matchedTerm = "Python"
                    )
                ))
            }
            override suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.success(emptyList())
            }
        }

        mockSkillRepository.skillsList.clear()
        val viewModel = ProfileViewModel(
            profileRepo = mockProfileRepository,
            skillRepo = mockSkillRepository,
            nlpRepo = mockNlpRepo
        )
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.BioChanged("Học Python cơ bản"))
        viewModel.onIntent(ProfileIntent.RequestSkillExtraction)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showSkillExtractionSheet)

        // Điều chỉnh proficiency level lên 4
        viewModel.onIntent(ProfileIntent.SetExtractedSkillLevel(5, 4))
        advanceUntilIdle()
        assertEquals(4, viewModel.state.value.extractedSkills.first().level)

        // Bấm xác nhận thêm vào hồ sơ
        viewModel.onIntent(ProfileIntent.ConfirmAddExtractedSkills)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showSkillExtractionSheet)
        // Kiểm tra skill 5 đã được thêm vào mySkills với type HAVE và level 4
        val added = viewModel.state.value.mySkills.find { it.skillId == 5 }
        assertNotNull(added)
        assertEquals(SkillType.HAVE, added!!.type)
        assertEquals(4, added.proficiencyLevel)
    }

    // ─── Document Upload Tests (TASK-043 Mức 1) ──────────────────────────

    @Test
    fun `RequestDocumentUpload sets showDocumentPrivacyDialog to true`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showDocumentPrivacyDialog)
        viewModel.onIntent(ProfileIntent.RequestDocumentUpload)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDocumentPrivacyDialog)
    }

    @Test
    fun `DismissDocumentPrivacyDialog sets showDocumentPrivacyDialog to false`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.RequestDocumentUpload)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showDocumentPrivacyDialog)

        viewModel.onIntent(ProfileIntent.DismissDocumentPrivacyDialog)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.showDocumentPrivacyDialog)
    }

    @Test
    fun `ConfirmDocumentPrivacyDialog hides dialog and emits OpenDocumentPicker effect`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ProfileIntent.RequestDocumentUpload)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showDocumentPrivacyDialog)

        viewModel.onIntent(ProfileIntent.ConfirmDocumentPrivacyDialog)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showDocumentPrivacyDialog)
        assertEquals(ProfileEffect.OpenDocumentPicker, effectReceived)
        job.cancel()
    }

    @Test
    fun `UploadDocument rejects zip file client-side`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        viewModel.onIntent(ProfileIntent.UploadDocument(zipBytes, "archive.zip"))
        advanceUntilIdle()

        assertTrue(effectReceived is ProfileEffect.ShowSnackbar)
        assertTrue((effectReceived as ProfileEffect.ShowSnackbar).message.contains("ZIP"))
        job.cancel()
    }

    @Test
    fun `UploadDocument rejects unsupported file extensions`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ProfileIntent.UploadDocument(byteArrayOf(1, 2, 3), "data.csv"))
        advanceUntilIdle()

        assertTrue(effectReceived is ProfileEffect.ShowSnackbar)
        assertTrue((effectReceived as ProfileEffect.ShowSnackbar).message.contains(".txt và .md"))
        job.cancel()
    }

    @Test
    fun `UploadDocument rejects oversized files over 2MB`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        val oversizedBytes = ByteArray(2 * 1024 * 1024 + 10) { 1 }
        viewModel.onIntent(ProfileIntent.UploadDocument(oversizedBytes, "large.txt"))
        advanceUntilIdle()

        assertTrue(effectReceived is ProfileEffect.ShowSnackbar)
        assertTrue((effectReceived as ProfileEffect.ShowSnackbar).message.contains("2MB"))
        job.cancel()
    }

    @Test
    fun `UploadDocument rejects empty file`() = runTest {
        val viewModel = ProfileViewModel(mockProfileRepository, mockSkillRepository)
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ProfileIntent.UploadDocument(ByteArray(0), "empty.txt"))
        advanceUntilIdle()

        assertTrue(effectReceived is ProfileEffect.ShowSnackbar)
        assertTrue((effectReceived as ProfileEffect.ShowSnackbar).message.contains("không có nội dung"))
        job.cancel()
    }

    @Test
    fun `UploadDocument success opens bottom sheet with default level 2 and filters existing HAVE skills`() = runTest {
        val mockNlpRepo = object : com.skillexchange.app.domain.repository.INlpRepository {
            override suspend fun extractSkills(text: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> =
                Result.success(emptyList())

            override suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.success(listOf(
                    com.skillexchange.app.data.remote.nlp.ExtractedSkillDto(
                        skillId = 10,
                        skillName = "Kotlin",
                        categoryId = 1,
                        categoryName = "Lập trình",
                        confidence = 1.0,
                        matchType = "EXACT",
                        matchedTerm = "Kotlin"
                    ),
                    com.skillexchange.app.data.remote.nlp.ExtractedSkillDto(
                        skillId = 20,
                        skillName = "Python",
                        categoryId = 1,
                        categoryName = "Lập trình",
                        confidence = 1.0,
                        matchType = "EXACT",
                        matchedTerm = "Python"
                    )
                ))
            }
        }

        // Đã có sẵn kỹ năng 10 trong HAVE
        mockSkillRepository.skillsList.clear()
        mockSkillRepository.skillsList.add(
            UserSkill("us10", 10, "Kotlin", "Lập trình", SkillType.HAVE, 4)
        )

        val viewModel = ProfileViewModel(
            profileRepo = mockProfileRepository,
            skillRepo = mockSkillRepository,
            nlpRepo = mockNlpRepo
        )
        advanceUntilIdle()

        val validBytes = "Kỹ năng: Kotlin, Python".toByteArray(Charsets.UTF_8)
        viewModel.onIntent(ProfileIntent.UploadDocument(validBytes, "cv.txt"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isExtractingDocument)
        assertTrue(state.showSkillExtractionSheet)
        assertEquals(2, state.extractedSkills.size)

        // Kỹ năng 10 đã có: isAlreadyAdded = true, isSelected = false
        val skill10 = state.extractedSkills.find { it.skill.skillId == 10 }!!
        assertTrue(skill10.isAlreadyAdded)
        assertFalse(skill10.isSelected)

        // Kỹ năng 20 chưa có: isAlreadyAdded = false, isSelected = true, level = 2
        val skill20 = state.extractedSkills.find { it.skill.skillId == 20 }!!
        assertFalse(skill20.isAlreadyAdded)
        assertTrue(skill20.isSelected)
        assertEquals(2, skill20.level)
    }

    @Test
    fun `UploadDocument server failure displays error in snackbar`() = runTest {
        val mockNlpRepo = object : com.skillexchange.app.domain.repository.INlpRepository {
            override suspend fun extractSkills(text: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> =
                Result.success(emptyList())

            override suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<com.skillexchange.app.data.remote.nlp.ExtractedSkillDto>> {
                return Result.failure(Exception("Nội dung văn bản vượt quá giới hạn 10.000 ký tự"))
            }
        }

        val viewModel = ProfileViewModel(
            profileRepo = mockProfileRepository,
            skillRepo = mockSkillRepository,
            nlpRepo = mockNlpRepo
        )
        advanceUntilIdle()

        var effectReceived: ProfileEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ProfileIntent.UploadDocument(ByteArray(50) { 65 }, "too_long.md"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExtractingDocument)
        assertFalse(viewModel.state.value.showSkillExtractionSheet)
        assertTrue(effectReceived is ProfileEffect.ShowSnackbar)
        assertTrue((effectReceived as ProfileEffect.ShowSnackbar).message.contains("10.000 ký tự"))
        job.cancel()
    }
}

