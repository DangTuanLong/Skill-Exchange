package com.skillexchange.app.presentation.profile.myprofile

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.friend.Friend
import com.skillexchange.app.domain.model.friend.FriendshipRelation
import com.skillexchange.app.domain.model.friend.FriendshipStatusType
import com.skillexchange.app.domain.model.rating.Rating
import com.skillexchange.app.domain.model.rating.Reputation
import com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus
import com.skillexchange.app.domain.repository.IFriendRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.IRatingRepository
import com.skillexchange.app.domain.repository.ISkillRepository
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
class MyProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply {
        saveUserId("my_user_id")
        saveAccessToken("valid_token")
        saveRefreshToken("valid_refresh_token")
    }

    private val fakeProfile = Profile(
        id = "p_1",
        userId = "my_user_id",
        fullName = "Nguyễn Văn Test",
        bio = "Yêu thích học hỏi",
        city = "Hà Nội",
        avatarUrl = "https://example.com/avatar.jpg",
        availability = listOf(
            AvailabilityWindow(day = "MON", from = "09:00", to = "11:00"),
            AvailabilityWindow(day = "WED", from = "14:00", to = "16:00")
        )
    )

    private val fakeSkills = listOf(
        UserSkill(id = "s_1", skillId = 1, skillName = "Kotlin", categoryName = "CNTT", type = SkillType.HAVE, proficiencyLevel = 4),
        UserSkill(id = "s_2", skillId = 2, skillName = "Tiếng Nhật", categoryName = "Ngoại ngữ", type = SkillType.WANT, proficiencyLevel = 2)
    )

    private val fakeReputation = Reputation(
        score = 4.8,
        badge = "Xuất sắc",
        totalExchanges = 12,
        completionRate = 0.95,
        ratingCount = 8,
        avgRating = 4.9
    )

    private val fakeFriends = listOf(
        Friend(
            friendshipId = "fs_1",
            userId = "friend_1",
            fullName = "Bạn A",
            avatarUrl = null,
            city = "Đà Nẵng",
            bio = null,
            status = "ACCEPTED",
            createdAt = "2026-10-12T00:00:00Z"
        )
    )

    private val fakeInvitations = listOf(
        Friend(
            friendshipId = "fs_2",
            userId = "inviter_1",
            fullName = "Người mời B",
            avatarUrl = null,
            city = "Hà Nội",
            bio = null,
            status = "PENDING",
            createdAt = "2026-10-12T01:00:00Z"
        )
    )

    private var profileResult: Result<Profile> = Result.success(fakeProfile)
    private var skillsResult: Result<List<UserSkill>> = Result.success(fakeSkills)
    private var reputationResult: Result<Reputation> = Result.success(fakeReputation)
    private var friendsResult: Result<List<Friend>> = Result.success(fakeFriends)
    private var pendingResult: Result<List<Friend>> = Result.success(fakeInvitations)

    private val mockProfileRepo = object : IProfileRepository {
        override suspend fun getMyProfile(): Result<Profile> = profileResult
        override suspend fun getUserProfile(userId: String): Result<Profile> = error("Not used")
        override suspend fun updateProfile(
            fullName: String, bio: String?, city: String?, avatarUrl: String?,
            availability: List<AvailabilityWindow>?
        ): Result<Profile> = error("Not used")
        override suspend fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = error("Not used")
    }

    private val mockSkillRepo = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())
        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> = skillsResult
        override suspend fun addUserSkill(skillId: Int, type: String, proficiencyLevel: Int, note: String?): Result<UserSkill> = error("Not used")
        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = Result.success(true)
    }

    private val mockRatingRepo = object : IRatingRepository {
        override suspend fun createRating(exchangeId: String, score: Int, comment: String?): Result<Rating> = error("Not used")
        override suspend fun getUserRatings(userId: String): Result<List<Rating>> = Result.success(emptyList())
        override suspend fun getUserReputation(userId: String): Result<Reputation> = reputationResult
        override suspend fun getMyExchangeRating(exchangeId: String): Result<UserExchangeRatingStatus> = error("Not used")
    }

    private val mockFriendRepo = object : IFriendRepository {
        override suspend fun sendFriendRequest(friendId: String): Result<Unit> = Result.success(Unit)
        override suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = Result.success(Unit)
        override suspend fun rejectFriendRequest(friendshipId: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeFriendship(friendshipId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getFriends(): Result<List<Friend>> = friendsResult
        override suspend fun getPendingRequests(): Result<List<Friend>> = pendingResult
        override suspend fun getFriendshipStatus(userId: String): Result<FriendshipRelation> =
            Result.success(FriendshipRelation(FriendshipStatusType.NONE))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tokenManager.saveUserId("my_user_id")
        tokenManager.saveAccessToken("valid_token")
        tokenManager.saveRefreshToken("valid_refresh_token")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads my profile, skills, reputation, and friends counts successfully`() = runTest {
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)

        assertEquals("Nguyễn Văn Test", state.profile?.fullName)
        assertEquals("Hà Nội", state.profile?.city)
        assertEquals(2, state.availability.size)

        assertEquals(1, state.haveSkills.size)
        assertEquals("Kotlin", state.haveSkills.first().skillName)

        assertEquals(1, state.wantSkills.size)
        assertEquals("Tiếng Nhật", state.wantSkills.first().skillName)

        assertEquals("Xuất sắc", state.reputation?.badge)
        assertEquals(1, state.friendsCount)
        assertEquals(1, state.pendingInvitationsCount)
    }

    @Test
    fun `initialization failure on getMyProfile sets error state`() = runTest {
        profileResult = Result.failure(RuntimeException("Lỗi tải hồ sơ cá nhân"))
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Lỗi tải hồ sơ cá nhân", state.error)
        assertNull(state.profile)
    }

    @Test
    fun `EditProfile intent emits NavigateToEditProfile effect`() = runTest {
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        var effectReceived: MyProfileEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(MyProfileIntent.EditProfile)
        advanceUntilIdle()

        assertTrue(effectReceived is MyProfileEffect.NavigateToEditProfile)
        job.cancel()
    }

    @Test
    fun `ManageSkills intent emits NavigateToManageSkills effect`() = runTest {
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        var effectReceived: MyProfileEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(MyProfileIntent.ManageSkills)
        advanceUntilIdle()

        assertTrue(effectReceived is MyProfileEffect.NavigateToManageSkills)
        job.cancel()
    }

    @Test
    fun `ViewFriends intent emits NavigateToFriends effect`() = runTest {
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        var effectReceived: MyProfileEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(MyProfileIntent.ViewFriends)
        advanceUntilIdle()

        assertTrue(effectReceived is MyProfileEffect.NavigateToFriends)
        job.cancel()
    }

    @Test
    fun `Logout intent clears session and emits NavigateToLogin effect`() = runTest {
        val viewModel = MyProfileViewModel(mockProfileRepo, mockSkillRepo, mockRatingRepo, mockFriendRepo, tokenManager)
        advanceUntilIdle()

        assertTrue(tokenManager.isLoggedIn())

        var effectReceived: MyProfileEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(MyProfileIntent.Logout)
        advanceUntilIdle()

        assertFalse(tokenManager.isLoggedIn())
        assertTrue(effectReceived is MyProfileEffect.NavigateToLogin)
        job.cancel()
    }
}
