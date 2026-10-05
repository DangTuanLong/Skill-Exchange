package com.skillexchange.app.presentation.discovery

import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.DiscoverySearchResult
import com.skillexchange.app.domain.repository.IDiscoveryRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockDiscoveryRepository = object : IDiscoveryRepository {
        var lastQuery: String? = null
        var lastCategories: List<Int>? = null
        var lastCity: String? = null
        var lastLastId: String? = null

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
            lastQuery = query
            lastCategories = categoryIds
            lastCity = city
            lastLastId = lastId

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
    }

    private val mockSkillRepository = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(
            listOf(
                SkillCategory(id = 1, name = "Lập trình", icon = "💻"),
                SkillCategory(id = 2, name = "Thiết kế", icon = "🎨")
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads categories and performs search`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.categories.size)
        assertEquals(1, state.usersList.size)
        assertEquals("Nguyễn Văn Test", state.usersList.first().fullName)
        assertEquals("cursor-page-2", state.nextCursor)
    }

    @Test
    fun `QueryChanged intent updates state query`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.QueryChanged("Kotlin"))
        assertEquals("Kotlin", viewModel.state.value.query)
    }

    @Test
    fun `CategorySelected intent toggles category and triggers search`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.CategorySelected(1))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedCategoryIds.contains(1))
        assertEquals(listOf(1), mockDiscoveryRepository.lastCategories)

        // Toggling same category unselects it
        viewModel.onIntent(DiscoveryIntent.CategorySelected(1))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedCategoryIds.isEmpty())
        assertNull(mockDiscoveryRepository.lastCategories)
    }

    @Test
    fun `ToggleCategory supports multiple category selection with OR semantics`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.ToggleCategory(1))
        viewModel.onIntent(DiscoveryIntent.ToggleCategory(2))
        advanceUntilIdle()

        assertEquals(setOf(1, 2), viewModel.state.value.selectedCategoryIds)
        assertEquals(listOf(1, 2), mockDiscoveryRepository.lastCategories)
        assertEquals(2, viewModel.state.value.activeFilterCount)
    }

    @Test
    fun `ClearCategories clears all selected category IDs`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.ToggleCategory(1))
        viewModel.onIntent(DiscoveryIntent.ToggleCategory(2))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.selectedCategoryIds.size)

        viewModel.onIntent(DiscoveryIntent.ClearCategories)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedCategoryIds.isEmpty())
        assertNull(mockDiscoveryRepository.lastCategories)
    }

    @Test
    fun `ResetFilters clears query and all filter selections`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.QueryChanged("React"))
        viewModel.onIntent(DiscoveryIntent.ToggleCategory(1))
        viewModel.onIntent(DiscoveryIntent.CitySelected("Hà Nội"))
        viewModel.onIntent(DiscoveryIntent.ResetFilters)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertTrue(state.selectedCategoryIds.isEmpty())
        assertNull(state.selectedCity)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun `LoadMore appends users from next page and updates cursor`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.usersList.size)
        assertEquals("cursor-page-2", viewModel.state.value.nextCursor)

        viewModel.onIntent(DiscoveryIntent.LoadMore)
        advanceUntilIdle()

        assertEquals("cursor-page-2", mockDiscoveryRepository.lastLastId)
        val state = viewModel.state.value
        assertEquals(2, state.usersList.size)
        assertEquals("Nguyễn Văn Test", state.usersList[0].fullName)
        assertEquals("Trần Thị B", state.usersList[1].fullName)
        assertNull(state.nextCursor)
    }
}
