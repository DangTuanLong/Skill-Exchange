package com.skillexchange.app.presentation.home

import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.DiscoverySearchResult
import com.skillexchange.app.domain.repository.IChatRepository
import com.skillexchange.app.domain.repository.IDiscoveryRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private var shouldFailSearch = false
    private var shouldFailMatching = false

    private val mockDiscoveryRepository = object : IDiscoveryRepository {
        var lastQuery: String? = null
        var lastCategories: List<Int>? = null
        var lastCity: String? = null
        var lastMinProficiency: Int? = null
        var lastMaxProficiency: Int? = null
        var lastType: String? = null
        var lastLastId: String? = null
        var searchCallCount = 0
        var matchingCallCount = 0

        override suspend fun searchUsers(
            query: String?,
            categoryIds: List<Int>?,
            city: String?,
            minProficiency: Int?,
            maxProficiency: Int?,
            type: String?,
            limit: Int,
            lastId: String?
        ): Result<DiscoverySearchResult> {
            searchCallCount++
            lastQuery = query
            lastCategories = categoryIds
            lastCity = city
            lastMinProficiency = minProficiency
            lastMaxProficiency = maxProficiency
            lastType = type
            lastLastId = lastId

            if (shouldFailSearch) {
                return Result.failure(Exception("Lỗi kết nối máy chủ"))
            }

            val users = if (lastId == null) {
                listOf(
                    UserDiscovery(
                        userId = "u1",
                        fullName = "Nguyễn Văn Test",
                        city = "Hà Nội",
                        skills = listOf(
                            UserSkill(skillId = 1, skillName = "Kotlin", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 4)
                        )
                    )
                )
            } else {
                listOf(
                    UserDiscovery(
                        userId = "u2",
                        fullName = "Trần Thị B",
                        city = "TP.HCM",
                        skills = listOf(
                            UserSkill(skillId = 2, skillName = "Figma", categoryName = "Thiết kế", type = SkillType.WANT, proficiencyLevel = 3)
                        )
                    )
                )
            }

            val nextCursor = if (lastId == null) "cursor-page-2" else null
            return Result.success(DiscoverySearchResult(users = users, nextCursor = nextCursor))
        }

        override suspend fun getMatchingSuggestions(limit: Int): Result<List<UserDiscovery>> {
            matchingCallCount++
            if (shouldFailMatching) {
                return Result.failure(Exception("Lỗi tải gợi ý"))
            }
            return Result.success(
                listOf(
                    UserDiscovery(
                        userId = "u_match_1",
                        fullName = "Lê Matching",
                        city = "Hà Nội",
                        matchPercentage = 95,
                        skills = listOf(
                            UserSkill(skillId = 1, skillName = "Kotlin", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 4)
                        )
                    )
                )
            )
        }
    }

    private val mockSkillRepository = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(
            listOf(
                SkillCategory(id = 1, name = "Lập trình", icon = "code"),
                SkillCategory(id = 2, name = "Thiết kế", icon = "palette")
            )
        )

        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> = Result.success(emptyList())

        override suspend fun addUserSkill(
            skillId: Int, type: String, proficiencyLevel: Int, note: String?
        ): Result<UserSkill> = error("Not needed")

        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = Result.success(true)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shouldFailSearch = false
        shouldFailMatching = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads categories and matching suggestions and searches all users`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.categories.size)
        assertEquals(1, state.matchingSuggestions.size)
        assertEquals("u_match_1", state.matchingSuggestions[0].userId)
        assertEquals(95, state.matchingSuggestions[0].matchPercentage)
        assertEquals(HomeTab.MATCHING, state.selectedTab)
        assertEquals(1, state.displayedUsers.size)
        assertEquals("u_match_1", state.displayedUsers[0].userId)
    }

    @Test
    fun `search query debounces 300ms and switches tab to ALL_USERS`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Type query
        viewModel.onIntent(HomeIntent.QueryChanged("Android"))
        assertEquals("Android", viewModel.state.value.query)

        // Advance 150ms: Debounce has not fired yet
        testDispatcher.scheduler.advanceTimeBy(150)
        assertNotEquals("Android", mockDiscoveryRepository.lastQuery)

        // Advance past 300ms
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Android", mockDiscoveryRepository.lastQuery)
        assertEquals(HomeTab.ALL_USERS, viewModel.state.value.selectedTab)
        assertEquals(1, viewModel.state.value.displayedUsers.size)
        assertEquals("u1", viewModel.state.value.displayedUsers[0].userId)
    }

    @Test
    fun `toggleCategory multi-select OR semantics and shared state`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle category 1
        viewModel.onIntent(HomeIntent.ToggleCategory(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedCategoryIds.contains(1))
        assertEquals(listOf(1), mockDiscoveryRepository.lastCategories)
        assertEquals(HomeTab.ALL_USERS, viewModel.state.value.selectedTab)

        // Toggle category 2 -> multi-select OR
        viewModel.onIntent(HomeIntent.ToggleCategory(2))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(1, 2), viewModel.state.value.selectedCategoryIds)
        assertEquals(listOf(1, 2), mockDiscoveryRepository.lastCategories)

        // Toggle category 1 again -> deselected
        viewModel.onIntent(HomeIntent.ToggleCategory(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(2), viewModel.state.value.selectedCategoryIds)
        assertEquals(listOf(2), mockDiscoveryRepository.lastCategories)

        // Clear categories
        viewModel.onIntent(HomeIntent.ClearCategories)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedCategoryIds.isEmpty())
        assertNull(mockDiscoveryRepository.lastCategories)
    }

    @Test
    fun `filterSheet filters by city, proficiency, type and resets correctly`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(HomeIntent.ToggleFilterSheet)
        assertTrue(viewModel.state.value.isFilterOpen)

        viewModel.onIntent(HomeIntent.CitySelected("Đà Nẵng"))
        viewModel.onIntent(HomeIntent.MinProficiencySelected(3))
        viewModel.onIntent(HomeIntent.MaxProficiencySelected(5))
        viewModel.onIntent(HomeIntent.TypeSelected("HAVE"))
        viewModel.onIntent(HomeIntent.PerformSearch)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Đà Nẵng", mockDiscoveryRepository.lastCity)
        assertEquals(3, mockDiscoveryRepository.lastMinProficiency)
        assertEquals(5, mockDiscoveryRepository.lastMaxProficiency)
        assertEquals("HAVE", mockDiscoveryRepository.lastType)

        // Reset
        viewModel.onIntent(HomeIntent.ResetFilters)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.selectedCity)
        assertNull(viewModel.state.value.minProficiency)
        assertNull(viewModel.state.value.maxProficiency)
        assertNull(viewModel.state.value.selectedType)
        assertTrue(viewModel.state.value.selectedCategoryIds.isEmpty())
    }

    @Test
    fun `switchTab toggles between matching and all users`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeTab.MATCHING, viewModel.state.value.selectedTab)
        assertEquals("u_match_1", viewModel.state.value.displayedUsers[0].userId)

        // Switch to ALL_USERS
        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeTab.ALL_USERS, viewModel.state.value.selectedTab)
        assertEquals("u1", viewModel.state.value.displayedUsers[0].userId)

        // Switch back to MATCHING
        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.MATCHING))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeTab.MATCHING, viewModel.state.value.selectedTab)
        assertEquals("u_match_1", viewModel.state.value.displayedUsers[0].userId)
    }

    @Test
    fun `loadMore appends users using cursor pagination`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.discoveryUsers.size)
        assertEquals("cursor-page-2", viewModel.state.value.nextCursor)

        // Trigger loadMore
        viewModel.onIntent(HomeIntent.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("cursor-page-2", mockDiscoveryRepository.lastLastId)
        assertEquals(2, viewModel.state.value.discoveryUsers.size)
        assertEquals("u1", viewModel.state.value.discoveryUsers[0].userId)
        assertEquals("u2", viewModel.state.value.discoveryUsers[1].userId)
        assertNull(viewModel.state.value.nextCursor)
    }

    @Test
    fun `error handling sets error and retry re-triggers search`() = runTest {
        shouldFailSearch = true
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Lỗi kết nối máy chủ", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)

        // Unset fail and retry
        shouldFailSearch = false
        viewModel.onIntent(HomeIntent.Retry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.discoveryUsers.size)
    }

    @Test
    fun `observes chatRoomsFlow and computes total unreadChatCount`() = runTest {
        val mockChatRepo = object : IChatRepository {
            override fun getChatRoomsFlow() = flowOf(
                listOf(
                    ChatRoom(
                        id = "c1",
                        exchangeId = "e1",
                        senderId = "u1",
                        receiverId = "u2",
                        senderName = "A",
                        receiverName = "B",
                        senderAvatarUrl = null,
                        receiverAvatarUrl = null,
                        skillOfferedName = "Kotlin",
                        skillWantedName = "Design",
                        status = "ACCEPTED",
                        lastMessage = "Hi",
                        lastMessageAt = 1000L,
                        lastSenderId = "u2",
                        unreadCount = 2
                    ),
                    ChatRoom(
                        id = "c2",
                        exchangeId = "e2",
                        senderId = "u1",
                        receiverId = "u3",
                        senderName = "A",
                        receiverName = "C",
                        senderAvatarUrl = null,
                        receiverAvatarUrl = null,
                        skillOfferedName = "Guitar",
                        skillWantedName = "English",
                        status = "ACCEPTED",
                        lastMessage = "Hello",
                        lastMessageAt = 2000L,
                        lastSenderId = "u3",
                        unreadCount = 3
                    )
                )
            )

            override fun getChatRoom(chatId: String): kotlinx.coroutines.flow.Flow<ChatRoom?> = flowOf(null)
            override fun getMessagesFlow(chatId: String, limit: Long): kotlinx.coroutines.flow.Flow<List<com.skillexchange.app.domain.model.chat.ChatMessage>> = flowOf(emptyList())
            override fun generateNewMessageId(chatId: String): String = "msg-1"
            override suspend fun sendMessage(chatId: String, content: String, participants: List<String>, messageId: String, type: String, fileUrl: String?, fileName: String?, fileSize: Long?) = Result.success("msg-1")
            override suspend fun uploadAttachment(chatId: String, fileBytes: ByteArray, fileName: String, mimeType: String) = error("Not needed")
            override suspend fun setTypingStatus(chatId: String, isTyping: Boolean) = Result.success(Unit)
            override fun observeOtherUserTyping(chatId: String, otherUserId: String) = flowOf(false)
            override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>) = Result.success(Unit)
        }

        val viewModel = HomeViewModel(
            discoveryRepository = mockDiscoveryRepository,
            skillRepository = mockSkillRepository,
            chatRepository = mockChatRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.state.value.unreadChatCount)
    }
}
