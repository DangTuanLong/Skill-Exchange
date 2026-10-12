package com.skillexchange.app.presentation.friend

import com.skillexchange.app.domain.model.friend.Friend
import com.skillexchange.app.domain.model.friend.FriendshipRelation
import com.skillexchange.app.domain.model.friend.FriendshipStatusType
import com.skillexchange.app.domain.repository.IFriendRepository
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
class FriendListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeFriends = listOf(
        Friend(
            friendshipId = "fs_1",
            userId = "user_2",
            fullName = "Trần Thị Bạn",
            avatarUrl = null,
            city = "Hà Nội",
            bio = "Lập trình viên",
            status = "ACCEPTED",
            createdAt = "2026-10-12T10:00:00Z"
        )
    )

    private val fakeInvitations = listOf(
        Friend(
            friendshipId = "fs_2",
            userId = "user_3",
            fullName = "Lê Văn Mời",
            avatarUrl = null,
            city = "Đà Nẵng",
            bio = "Thích học ngoại ngữ",
            status = "PENDING",
            createdAt = "2026-10-12T11:00:00Z"
        )
    )

    private var getFriendsResult: Result<List<Friend>> = Result.success(fakeFriends)
    private var getPendingRequestsResult: Result<List<Friend>> = Result.success(fakeInvitations)
    private var acceptResult: Result<Unit> = Result.success(Unit)
    private var rejectResult: Result<Unit> = Result.success(Unit)
    private var removeResult: Result<Unit> = Result.success(Unit)

    var lastAcceptedId: String? = null
    var lastRejectedId: String? = null
    var lastRemovedId: String? = null

    private val mockFriendRepository = object : IFriendRepository {
        override suspend fun sendFriendRequest(friendId: String): Result<Unit> = Result.success(Unit)
        override suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> {
            lastAcceptedId = friendshipId
            return acceptResult
        }
        override suspend fun rejectFriendRequest(friendshipId: String): Result<Unit> {
            lastRejectedId = friendshipId
            return rejectResult
        }
        override suspend fun removeFriendship(friendshipId: String): Result<Unit> {
            lastRemovedId = friendshipId
            return removeResult
        }
        override suspend fun getFriends(): Result<List<Friend>> = getFriendsResult
        override suspend fun getPendingRequests(): Result<List<Friend>> = getPendingRequestsResult
        override suspend fun getFriendshipStatus(userId: String): Result<FriendshipRelation> =
            Result.success(FriendshipRelation(FriendshipStatusType.NONE))
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
    fun `initial load populates friends and invitations successfully`() = runTest {
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.friends.size)
        assertEquals("user_2", state.friends.first().userId)
        assertEquals(1, state.invitations.size)
        assertEquals("user_3", state.invitations.first().userId)
        assertEquals(FriendTab.FRIENDS, state.selectedTab)
    }

    @Test
    fun `initial load failure sets error in state`() = runTest {
        getFriendsResult = Result.failure(RuntimeException("Lỗi kết nối mạng"))
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Lỗi kết nối mạng", state.error)
    }

    @Test
    fun `SelectTab updates current selectedTab`() = runTest {
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        viewModel.onIntent(FriendListIntent.SelectTab(FriendTab.INVITATIONS))
        assertEquals(FriendTab.INVITATIONS, viewModel.state.value.selectedTab)

        viewModel.onIntent(FriendListIntent.SelectTab(FriendTab.FRIENDS))
        assertEquals(FriendTab.FRIENDS, viewModel.state.value.selectedTab)
    }

    @Test
    fun `AcceptInvitation calls repository and sends success snackbar`() = runTest {
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        var effectReceived: FriendListEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(FriendListIntent.AcceptInvitation("fs_2"))
        advanceUntilIdle()

        assertEquals("fs_2", lastAcceptedId)
        assertTrue(effectReceived is FriendListEffect.ShowSnackbar)
        assertEquals("Đã chấp nhận lời mời kết bạn!", (effectReceived as FriendListEffect.ShowSnackbar).message)
        job.cancel()
    }

    @Test
    fun `AcceptInvitation failure sends error snackbar`() = runTest {
        acceptResult = Result.failure(RuntimeException("Không thể chấp nhận"))
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        var effectReceived: FriendListEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(FriendListIntent.AcceptInvitation("fs_2"))
        advanceUntilIdle()

        assertEquals("fs_2", lastAcceptedId)
        assertTrue(effectReceived is FriendListEffect.ShowSnackbar)
        assertEquals("Không thể chấp nhận", (effectReceived as FriendListEffect.ShowSnackbar).message)
        job.cancel()
    }

    @Test
    fun `RejectInvitation calls repository and sends success snackbar`() = runTest {
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        var effectReceived: FriendListEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(FriendListIntent.RejectInvitation("fs_2"))
        advanceUntilIdle()

        assertEquals("fs_2", lastRejectedId)
        assertTrue(effectReceived is FriendListEffect.ShowSnackbar)
        assertEquals("Đã từ chối lời mời kết bạn.", (effectReceived as FriendListEffect.ShowSnackbar).message)
        job.cancel()
    }

    @Test
    fun `RemoveFriend calls repository and sends success snackbar`() = runTest {
        val viewModel = FriendListViewModel(mockFriendRepository)
        advanceUntilIdle()

        var effectReceived: FriendListEffect? = null
        val job = launch {
            effectReceived = viewModel.effect.first()
        }

        viewModel.onIntent(FriendListIntent.RemoveFriend("fs_1"))
        advanceUntilIdle()

        assertEquals("fs_1", lastRemovedId)
        assertTrue(effectReceived is FriendListEffect.ShowSnackbar)
        assertEquals("Đã hủy kết bạn.", (effectReceived as FriendListEffect.ShowSnackbar).message)
        job.cancel()
    }
}
