package com.skillexchange.app.presentation.home

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.PotentialMatch
import com.skillexchange.app.domain.model.PotentialMatchSkill
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
    private var returnEmptyUsers = false
    private var returnEmptyMatching = false

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
        var potentialCallCount = 0
        var potentialMatchesToReturn = listOf(
            PotentialMatch(
                userId = "u_pot_1",
                fullName = "Trần Tiềm Năng",
                canTeachSkill = PotentialMatchSkill(skillId = 1, skillName = "Kotlin", proficiencyLevel = 4),
                suggestedWantSkill = PotentialMatchSkill(skillId = 20, skillName = "Tiếng Nhật", proficiencyLevel = 2),
                potentialScore = 80
            )
        )

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

            if (returnEmptyUsers) {
                return Result.success(DiscoverySearchResult(users = emptyList(), nextCursor = null))
            }

            val users = if (lastId == null) {
                listOf(
                    UserDiscovery(
                        userId = "u1",
                        fullName = "Nguyễn Văn Test",
                        city = "Hà Nội",
                        skills = listOf(
                            UserSkill(skillId = 1, skillName = "Kotlin", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 4)
                        ),
                        relationshipStatus = "ACTIVE",
                        completedExchangeCount = 2,
                        availablePairsCount = 3
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
                        ),
                        relationshipStatus = "COMPLETED",
                        completedExchangeCount = 1
                    )
                )
            }

            val nextCursor = if (lastId == null) "cursor-page-2" else null
            return Result.success(DiscoverySearchResult(users = users, nextCursor = nextCursor))
        }

        override suspend fun getMatchingSuggestions(limit: Int, cursor: String?): Result<List<UserDiscovery>> {
            matchingCallCount++
            if (shouldFailMatching) {
                return Result.failure(Exception("Lỗi tải gợi ý"))
            }
            if (returnEmptyMatching) {
                return Result.success(emptyList())
            }
            return Result.success(
                listOf(
                    UserDiscovery(
                        userId = "u_match_1",
                        fullName = "Lê Matching",
                        city = "Hà Nội",
                        matchPercentage = 95,
                        availablePairsCount = 2,
                        skills = listOf(
                            UserSkill(skillId = 1, skillName = "Kotlin", categoryName = "Lập trình", type = SkillType.HAVE, proficiencyLevel = 4)
                        )
                    )
                )
            )
        }

        override suspend fun getPotentialSuggestions(limit: Int, cursor: String?): Result<List<PotentialMatch>> {
            potentialCallCount++
            return Result.success(potentialMatchesToReturn)
        }
    }

    private val mockSkillRepository = object : ISkillRepository {
        var addedSkillId: Int? = null
        var addedSkillType: String? = null
        var addedSkillLevel: Int? = null
        var removedSkillId: String? = null
        var removeCallCount = 0
        var userSkillsToReturn = mutableListOf<UserSkill>()

        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(
            listOf(
                SkillCategory(id = 1, name = "Lập trình", icon = "code"),
                SkillCategory(id = 2, name = "Thiết kế", icon = "palette")
            )
        )

        override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> =
            Result.success(userSkillsToReturn)

        override suspend fun addUserSkill(
            skillId: Int, type: String, proficiencyLevel: Int, note: String?
        ): Result<UserSkill> {
            addedSkillId = skillId
            addedSkillType = type
            addedSkillLevel = proficiencyLevel
            return Result.success(
                UserSkill(
                    id = "user_skill_created_123",
                    skillId = skillId,
                    skillName = "Kỹ năng $skillId",
                    type = if (type == "HAVE") SkillType.HAVE else SkillType.WANT,
                    proficiencyLevel = proficiencyLevel
                )
            )
        }

        override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> {
            removeCallCount++
            removedSkillId = userSkillId
            return Result.success(true)
        }
    }

    private val testTokenManager = object : TokenManager(null) {
        override fun getUserId(): String? = "test_current_user_id"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shouldFailSearch = false
        shouldFailMatching = false
        returnEmptyUsers = false
        returnEmptyMatching = false
        mockDiscoveryRepository.searchCallCount = 0
        mockDiscoveryRepository.matchingCallCount = 0
        mockDiscoveryRepository.potentialCallCount = 0
        mockSkillRepository.removeCallCount = 0
        mockSkillRepository.removedSkillId = null
        mockSkillRepository.addedSkillId = null
        mockSkillRepository.userSkillsToReturn.clear()
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

    @Test
    fun `matching error sets error and retry recovers matching suggestions`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.matchingSuggestions.size)

        shouldFailMatching = true
        viewModel.loadMatchingSuggestions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Lỗi tải gợi ý", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)

        // Retry matching
        shouldFailMatching = false
        viewModel.onIntent(HomeIntent.Retry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.matchingSuggestions.size)
        assertEquals("u_match_1", viewModel.state.value.matchingSuggestions[0].userId)
    }

    @Test
    fun `switching tabs preserves selectedCategoryIds and filter criteria without resetting state`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set filters
        viewModel.onIntent(HomeIntent.ToggleCategory(1))
        viewModel.onIntent(HomeIntent.CitySelected("Hà Nội"))
        viewModel.onIntent(HomeIntent.MinProficiencySelected(2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeTab.ALL_USERS, viewModel.state.value.selectedTab)
        assertEquals(setOf(1), viewModel.state.value.selectedCategoryIds)
        assertEquals("Hà Nội", viewModel.state.value.selectedCity)
        assertEquals(2, viewModel.state.value.minProficiency)

        // Switch to MATCHING
        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.MATCHING))
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify preserved
        assertEquals(HomeTab.MATCHING, viewModel.state.value.selectedTab)
        assertEquals(setOf(1), viewModel.state.value.selectedCategoryIds)
        assertEquals("Hà Nội", viewModel.state.value.selectedCity)
        assertEquals(2, viewModel.state.value.minProficiency)

        // Switch back to ALL_USERS
        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeTab.ALL_USERS, viewModel.state.value.selectedTab)
        assertEquals(setOf(1), viewModel.state.value.selectedCategoryIds)
        assertEquals("Hà Nội", viewModel.state.value.selectedCity)
        assertEquals(2, viewModel.state.value.minProficiency)
    }

    @Test
    fun `search returns empty result list when no users match and handles gracefully`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.displayedUsers.size)

        // Return empty users
        returnEmptyUsers = true
        viewModel.onIntent(HomeIntent.PerformSearch)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.discoveryUsers.isEmpty())
        assertTrue(viewModel.state.value.displayedUsers.isEmpty())
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `clearing search query restores default discovery users`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Type query
        viewModel.onIntent(HomeIntent.QueryChanged("Python"))
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Python", mockDiscoveryRepository.lastQuery)

        // Clear query
        viewModel.onIntent(HomeIntent.QueryChanged(""))
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.state.value.query)
        assertNull(mockDiscoveryRepository.lastQuery)
        assertEquals(1, viewModel.state.value.discoveryUsers.size)
    }

    @Test
    fun `search results include relationshipStatus completedExchangeCount and availablePairsCount safely`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        // Tab ALL_USERS
        viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS))
        testDispatcher.scheduler.advanceUntilIdle()

        val users = viewModel.state.value.displayedUsers
        assertEquals(1, users.size)
        val user1 = users[0]
        assertEquals("u1", user1.userId)
        assertEquals("ACTIVE", user1.relationshipStatus)
        assertEquals(2, user1.completedExchangeCount)
        assertEquals(3, user1.availablePairsCount)
    }

    @Test
    fun `empty matching suggestions at first page triggers potential suggestions fetch`() = runTest {
        returnEmptyMatching = true
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, mockDiscoveryRepository.potentialCallCount)
        val state = viewModel.state.value
        assertTrue(state.matchingSuggestions.isEmpty())
        assertEquals(1, state.potentialMatches.size)
        assertEquals("u_pot_1", state.potentialMatches[0].userId)
        assertEquals(80, state.potentialMatches[0].potentialScore)
        assertEquals("Kotlin", state.potentialMatches[0].canTeachSkill.skillName)
        assertEquals("Tiếng Nhật", state.potentialMatches[0].suggestedWantSkill.skillName)
    }

    @Test
    fun `non-empty matching suggestions does NOT trigger potential suggestions`() = runTest {
        returnEmptyMatching = false
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, mockDiscoveryRepository.potentialCallCount)
        val state = viewModel.state.value
        assertEquals(1, state.matchingSuggestions.size)
        assertTrue(state.potentialMatches.isEmpty())
    }

    @Test
    fun `requestAddPotentialSkill sets pendingSkillToAdd and dismissAddSkillDialog clears it`() = runTest {
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val skill = PotentialMatchSkill(skillId = 10, skillName = "Thiết kế đồ họa", proficiencyLevel = 3)
        viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
        assertEquals(skill, viewModel.state.value.pendingSkillToAdd)

        viewModel.onIntent(HomeIntent.DismissAddSkillDialog)
        assertNull(viewModel.state.value.pendingSkillToAdd)
    }

    @Test
    fun `confirmAddSkill adds skill to WANT with default level 1 and sets pendingUndoSkillId for newly created skill`() = runTest {
        returnEmptyMatching = true
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val skill = PotentialMatchSkill(skillId = 20, skillName = "Tiếng Nhật", proficiencyLevel = 2)
        viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
        viewModel.onIntent(HomeIntent.ConfirmAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, mockSkillRepository.addedSkillId)
        assertEquals("WANT", mockSkillRepository.addedSkillType)
        assertEquals(1, mockSkillRepository.addedSkillLevel) // Mức mặc định thấp 1/5
        assertEquals("user_skill_created_123", viewModel.state.value.pendingUndoSkillId)
        assertEquals("Tiếng Nhật", viewModel.state.value.pendingUndoSkillName)
    }

    @Test
    fun `undoAddSkill deletes newly created skill and refreshes matching suggestions`() = runTest {
        returnEmptyMatching = true
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val skill = PotentialMatchSkill(skillId = 20, skillName = "Tiếng Nhật", proficiencyLevel = 2)
        viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
        viewModel.onIntent(HomeIntent.ConfirmAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user_skill_created_123", viewModel.state.value.pendingUndoSkillId)

        // Undo
        viewModel.onIntent(HomeIntent.UndoAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, mockSkillRepository.removeCallCount)
        assertEquals("user_skill_created_123", mockSkillRepository.removedSkillId)
        assertNull(viewModel.state.value.pendingUndoSkillId)
        assertNull(viewModel.state.value.pendingUndoSkillName)
    }

    @Test
    fun `undoAddSkill does not delete skill if it already existed in WANT beforehand`() = runTest {
        returnEmptyMatching = true
        mockSkillRepository.userSkillsToReturn.add(
            UserSkill(id = "old_skill_999", skillId = 20, skillName = "Tiếng Nhật", type = SkillType.WANT, proficiencyLevel = 2)
        )
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val skill = PotentialMatchSkill(skillId = 20, skillName = "Tiếng Nhật", proficiencyLevel = 2)
        viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
        viewModel.onIntent(HomeIntent.ConfirmAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        // Since it was already present, pendingUndoSkillId must be null
        assertNull(viewModel.state.value.pendingUndoSkillId)

        // Undo
        viewModel.onIntent(HomeIntent.UndoAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        // Must NOT call removeUserSkill
        assertEquals(0, mockSkillRepository.removeCallCount)
    }

    @Test
    fun `clearPendingUndo clears pending undo on dispose or timeout`() = runTest {
        returnEmptyMatching = true
        val viewModel = HomeViewModel(mockDiscoveryRepository, mockSkillRepository, tokenManager = testTokenManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val skill = PotentialMatchSkill(skillId = 20, skillName = "Tiếng Nhật", proficiencyLevel = 2)
        viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
        viewModel.onIntent(HomeIntent.ConfirmAddSkill)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user_skill_created_123", viewModel.state.value.pendingUndoSkillId)

        viewModel.onIntent(HomeIntent.ClearPendingUndo)
        assertNull(viewModel.state.value.pendingUndoSkillId)
        assertNull(viewModel.state.value.pendingUndoSkillName)
    }
}
