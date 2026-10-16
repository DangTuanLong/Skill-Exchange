package com.skillexchange.app.presentation.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.repository.GraphUserNode
import com.skillexchange.app.domain.repository.IGraphRepository
import com.skillexchange.app.domain.repository.SuggestedLoop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GraphLoopTab {
    MULTI_WAY, // Vòng 3-4 người
    TWO_WAY    // Vòng 2 người song phương
}

data class SkillGraphUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: GraphLoopTab = GraphLoopTab.MULTI_WAY,
    val loops2: List<SuggestedLoop> = emptyList(),
    val loops3Plus: List<SuggestedLoop> = emptyList(),
    val selectedLoopIndex: Int = 0,
    val selectedNode: GraphUserNode? = null,
    val disclaimer: String = "Trao đổi vòng tròn là gợi ý kết nối gián tiếp. Hệ thống không tự tạo trao đổi ba bên; bạn hãy chủ động gửi yêu cầu trao đổi 1-1 tới đối tác liên quan."
) {
    val currentLoops: List<SuggestedLoop>
        get() = when (selectedTab) {
            GraphLoopTab.TWO_WAY -> loops2
            GraphLoopTab.MULTI_WAY -> loops3Plus
        }

    val currentLoop: SuggestedLoop?
        get() = currentLoops.getOrNull(selectedLoopIndex)
}

sealed interface SkillGraphIntent {
    data object LoadGraph : SkillGraphIntent
    data class SelectTab(val tab: GraphLoopTab) : SkillGraphIntent
    data class SelectLoop(val index: Int) : SkillGraphIntent
    data class SelectNode(val node: GraphUserNode) : SkillGraphIntent
    data object DismissNodeDetail : SkillGraphIntent
}

class SkillGraphViewModel(
    private val graphRepository: IGraphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillGraphUiState())
    val uiState: StateFlow<SkillGraphUiState> = _uiState.asStateFlow()

    init {
        handleIntent(SkillGraphIntent.LoadGraph)
    }

    fun handleIntent(intent: SkillGraphIntent) {
        when (intent) {
            is SkillGraphIntent.LoadGraph -> loadGraph()
            is SkillGraphIntent.SelectTab -> {
                _uiState.update {
                    it.copy(
                        selectedTab = intent.tab,
                        selectedLoopIndex = 0,
                        selectedNode = null
                    )
                }
            }
            is SkillGraphIntent.SelectLoop -> {
                _uiState.update {
                    it.copy(
                        selectedLoopIndex = intent.index,
                        selectedNode = null
                    )
                }
            }
            is SkillGraphIntent.SelectNode -> {
                _uiState.update { it.copy(selectedNode = intent.node) }
            }
            is SkillGraphIntent.DismissNodeDetail -> {
                _uiState.update { it.copy(selectedNode = null) }
            }
        }
    }

    private fun loadGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            graphRepository.getSuggestedPaths(depth = 4, limit = 5)
                .onSuccess { result ->
                    // Mặc định chọn tab MULTI_WAY nếu có vòng 3+, nếu không thì TWO_WAY
                    val initialTab = if (result.loops3Plus.isNotEmpty()) {
                        GraphLoopTab.MULTI_WAY
                    } else {
                        GraphLoopTab.TWO_WAY
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            selectedTab = initialTab,
                            loops2 = result.loops2,
                            loops3Plus = result.loops3Plus,
                            selectedLoopIndex = 0,
                            selectedNode = null,
                            disclaimer = result.disclaimer
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Không thể tải sơ đồ trao đổi. Vui lòng thử lại."
                        )
                    }
                }
        }
    }
}
