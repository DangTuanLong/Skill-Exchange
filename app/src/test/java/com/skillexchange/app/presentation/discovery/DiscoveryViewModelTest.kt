package com.skillexchange.app.presentation.discovery

import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.model.UserSkill
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
        var lastCategory: Int? = null
        var lastCity: String? = null

        override suspend fun searchUsers(
            query: String?,
            categoryId: Int?,
            city: String?,
            minProficiency: Int?,
            type: String?,
            limit: Int,
            offset: Int
        ): Result<List<UserDiscovery>> {
            lastQuery = query
            lastCategory = categoryId
            lastCity = city
            return Result.success(
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
            )
        }
    }

    private val mockSkillRepository = object : ISkillRepository {
        override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(
            listOf(SkillCategory(id = 1, name = "Lập trình", icon = "💻"))
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
        assertEquals(1, state.categories.size)
        assertEquals(1, state.usersList.size)
        assertEquals("Nguyễn Văn Test", state.usersList.first().fullName)
    }

    @Test
    fun `QueryChanged intent updates state query`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.QueryChanged("Kotlin"))
        assertEquals("Kotlin", viewModel.state.value.query)
    }

    @Test
    fun `CategorySelected intent toggles selected category and triggers search`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.CategorySelected(1))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.selectedCategoryId)
        assertEquals(1, mockDiscoveryRepository.lastCategory)

        // Toggling same category unselects it
        viewModel.onIntent(DiscoveryIntent.CategorySelected(1))
        advanceUntilIdle()
        assertNull(viewModel.state.value.selectedCategoryId)
    }

    @Test
    fun `ResetFilters clears query and filter selections`() = runTest {
        val viewModel = DiscoveryViewModel(mockDiscoveryRepository, mockSkillRepository)
        advanceUntilIdle()

        viewModel.onIntent(DiscoveryIntent.QueryChanged("React"))
        viewModel.onIntent(DiscoveryIntent.CategorySelected(1))
        viewModel.onIntent(DiscoveryIntent.CitySelected("Hà Nội"))
        viewModel.onIntent(DiscoveryIntent.ResetFilters)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertNull(state.selectedCategoryId)
        assertNull(state.selectedCity)
    }
}
