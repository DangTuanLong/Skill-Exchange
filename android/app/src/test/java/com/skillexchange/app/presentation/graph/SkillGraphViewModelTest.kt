package com.skillexchange.app.presentation.graph

import com.skillexchange.app.domain.repository.GraphEdge
import com.skillexchange.app.domain.repository.GraphUserNode
import com.skillexchange.app.domain.repository.IGraphRepository
import com.skillexchange.app.domain.repository.SuggestPathResult
import com.skillexchange.app.domain.repository.SuggestedLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SkillGraphViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleNodeA = GraphUserNode("user-a", "User A", city = "Hà Nội")
    private val sampleNodeB = GraphUserNode("user-b", "User B", city = "TP.HCM")
    private val sampleNodeC = GraphUserNode("user-c", "User C", city = "Đà Nẵng")

    private val sampleLoop2 = SuggestedLoop(
        loopId = "loop-2-id",
        cycleLength = 2,
        nodes = listOf(sampleNodeA, sampleNodeB),
        edges = listOf(
            GraphEdge("user-a", "user-b", 1, "Kotlin", 4, 2),
            GraphEdge("user-b", "user-a", 2, "Python", 3, 2)
        )
    )

    private val sampleLoop3 = SuggestedLoop(
        loopId = "loop-3-id",
        cycleLength = 3,
        nodes = listOf(sampleNodeA, sampleNodeB, sampleNodeC),
        edges = listOf(
            GraphEdge("user-a", "user-b", 10, "Figma", 4, 2),
            GraphEdge("user-b", "user-c", 20, "Guitar", 3, 1),
            GraphEdge("user-c", "user-a", 30, "Tiếng Anh", 5, 2)
        )
    )

    private class FakeGraphRepository(
        var result: Result<SuggestPathResult>
    ) : IGraphRepository {
        override suspend fun getSuggestedPaths(depth: Int, limit: Int): Result<SuggestPathResult> = result
    }

    @Test
    fun `initial load sets loops and selects MULTI_WAY tab when loops3Plus is not empty`() = runTest {
        val fakeRepo = FakeGraphRepository(
            Result.success(
                SuggestPathResult(
                    callerId = "user-a",
                    loops2 = listOf(sampleLoop2),
                    loops3Plus = listOf(sampleLoop3),
                    disclaimer = "Test disclaimer"
                )
            )
        )

        val viewModel = SkillGraphViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(GraphLoopTab.MULTI_WAY, state.selectedTab)
        assertEquals(1, state.loops2.size)
        assertEquals(1, state.loops3Plus.size)
        assertEquals(sampleLoop3, state.currentLoop)
        assertEquals("Test disclaimer", state.disclaimer)
    }

    @Test
    fun `initial load selects TWO_WAY tab when loops3Plus is empty`() = runTest {
        val fakeRepo = FakeGraphRepository(
            Result.success(
                SuggestPathResult(
                    callerId = "user-a",
                    loops2 = listOf(sampleLoop2),
                    loops3Plus = emptyList(),
                    disclaimer = "Test disclaimer"
                )
            )
        )

        val viewModel = SkillGraphViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GraphLoopTab.TWO_WAY, state.selectedTab)
        assertEquals(sampleLoop2, state.currentLoop)
    }

    @Test
    fun `SelectTab switches tab and resets selectedLoopIndex`() = runTest {
        val fakeRepo = FakeGraphRepository(
            Result.success(
                SuggestPathResult(
                    callerId = "user-a",
                    loops2 = listOf(sampleLoop2),
                    loops3Plus = listOf(sampleLoop3),
                    disclaimer = "Test disclaimer"
                )
            )
        )

        val viewModel = SkillGraphViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.handleIntent(SkillGraphIntent.SelectTab(GraphLoopTab.TWO_WAY))
        val state = viewModel.uiState.value
        assertEquals(GraphLoopTab.TWO_WAY, state.selectedTab)
        assertEquals(0, state.selectedLoopIndex)
        assertEquals(sampleLoop2, state.currentLoop)
    }

    @Test
    fun `SelectNode and DismissNodeDetail update selectedNode`() = runTest {
        val fakeRepo = FakeGraphRepository(
            Result.success(
                SuggestPathResult(
                    callerId = "user-a",
                    loops2 = listOf(sampleLoop2),
                    loops3Plus = listOf(sampleLoop3),
                    disclaimer = "Test disclaimer"
                )
            )
        )

        val viewModel = SkillGraphViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.handleIntent(SkillGraphIntent.SelectNode(sampleNodeB))
        assertEquals(sampleNodeB, viewModel.uiState.value.selectedNode)

        viewModel.handleIntent(SkillGraphIntent.DismissNodeDetail)
        assertNull(viewModel.uiState.value.selectedNode)
    }

    @Test
    fun `handles error state on failure`() = runTest {
        val fakeRepo = FakeGraphRepository(
            Result.failure(RuntimeException("Lỗi kết nối mạng"))
        )

        val viewModel = SkillGraphViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Lỗi kết nối mạng", state.error)
    }
}
