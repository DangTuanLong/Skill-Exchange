package com.skillexchange.app.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.repository.IDiscoveryRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── State ────────────────────────────────────────────────────────────
data class DiscoveryUiState(
    val query: String = "",
    val selectedCategoryIds: Set<Int> = emptySet(),
    val selectedCity: String? = null,
    val minProficiency: Int? = null,
    val maxProficiency: Int? = null,
    val selectedType: String? = null,    // "HAVE" | "WANT" | null
    val usersList: List<UserDiscovery> = emptyList(),
    val categories: List<SkillCategory> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val isFilterOpen: Boolean = false,
    val error: String? = null
) {
    // Backward-compatible single category getter for existing call sites
    val selectedCategoryId: Int?
        get() = selectedCategoryIds.firstOrNull()

    // Số filter đang bật (tính cả từng category đã chọn per DEC-014)
    val activeFilterCount: Int
        get() {
            var count = selectedCategoryIds.size
            if (!selectedCity.isNullOrBlank()) count++
            if (minProficiency != null || maxProficiency != null) count++
            if (!selectedType.isNullOrBlank()) count++
            return count
        }
}

// ─── Intent ───────────────────────────────────────────────────────────
sealed class DiscoveryIntent {
    data class QueryChanged(val value: String) : DiscoveryIntent()
    data class CategorySelected(val categoryId: Int?) : DiscoveryIntent()
    data class ToggleCategory(val categoryId: Int) : DiscoveryIntent()
    object ClearCategories : DiscoveryIntent()
    data class CitySelected(val city: String?) : DiscoveryIntent()
    data class MinProficiencySelected(val level: Int?) : DiscoveryIntent()
    data class MaxProficiencySelected(val level: Int?) : DiscoveryIntent()
    data class TypeSelected(val type: String?) : DiscoveryIntent()
    object ToggleFilterSheet : DiscoveryIntent()
    object ResetFilters : DiscoveryIntent()
    object PerformSearch : DiscoveryIntent()
    object LoadMore : DiscoveryIntent()
}

// ─── Effect ───────────────────────────────────────────────────────────
sealed class DiscoveryEffect {
    data class ShowSnackbar(val message: String) : DiscoveryEffect()
    data class NavigateToProfileDetail(val userId: String) : DiscoveryEffect()
}

class DiscoveryViewModel(
    private val discoveryRepository: IDiscoveryRepository,
    private val skillRepository: ISkillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<DiscoveryEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadCategories()
        performSearch()
    }

    fun onIntent(intent: DiscoveryIntent) {
        when (intent) {
            is DiscoveryIntent.QueryChanged -> _state.update { it.copy(query = intent.value) }

            is DiscoveryIntent.CategorySelected -> {
                // Nếu null -> clear; nếu có id -> toggle (bật/tắt)
                _state.update { current ->
                    val newSelection = if (intent.categoryId == null) {
                        emptySet()
                    } else if (current.selectedCategoryIds.contains(intent.categoryId)) {
                        current.selectedCategoryIds - intent.categoryId
                    } else {
                        current.selectedCategoryIds + intent.categoryId
                    }
                    current.copy(selectedCategoryIds = newSelection)
                }
                performSearch()
            }

            is DiscoveryIntent.ToggleCategory -> {
                _state.update { current ->
                    val newSelection = if (current.selectedCategoryIds.contains(intent.categoryId)) {
                        current.selectedCategoryIds - intent.categoryId
                    } else {
                        current.selectedCategoryIds + intent.categoryId
                    }
                    current.copy(selectedCategoryIds = newSelection)
                }
                performSearch()
            }

            is DiscoveryIntent.ClearCategories -> {
                _state.update { it.copy(selectedCategoryIds = emptySet()) }
                performSearch()
            }

            is DiscoveryIntent.CitySelected -> _state.update { it.copy(selectedCity = intent.city) }

            is DiscoveryIntent.MinProficiencySelected -> _state.update { it.copy(minProficiency = intent.level) }

            is DiscoveryIntent.MaxProficiencySelected -> _state.update { it.copy(maxProficiency = intent.level) }

            is DiscoveryIntent.TypeSelected -> _state.update { it.copy(selectedType = intent.type) }

            is DiscoveryIntent.ToggleFilterSheet -> _state.update { it.copy(isFilterOpen = !it.isFilterOpen) }

            is DiscoveryIntent.ResetFilters -> {
                _state.update {
                    it.copy(
                        query = "",
                        selectedCategoryIds = emptySet(),
                        selectedCity = null,
                        minProficiency = null,
                        maxProficiency = null,
                        selectedType = null
                    )
                }
                performSearch()
            }

            is DiscoveryIntent.PerformSearch -> performSearch()

            is DiscoveryIntent.LoadMore -> loadMore()
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            skillRepository.getCategories().onSuccess { cats ->
                _state.update { it.copy(categories = cats) }
            }
        }
    }

    private fun performSearch() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, nextCursor = null) }
            val catList = s.selectedCategoryIds.toList().takeIf { it.isNotEmpty() }
            discoveryRepository.searchUsers(
                query = s.query.ifBlank { null },
                categoryIds = catList,
                city = s.selectedCity?.ifBlank { null },
                minProficiency = s.minProficiency,
                maxProficiency = s.maxProficiency,
                type = s.selectedType,
                limit = 20,
                lastId = null
            )
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            usersList = result.users,
                            nextCursor = result.nextCursor
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Lỗi tìm kiếm") }
                    _effect.send(DiscoveryEffect.ShowSnackbar(e.message ?: "Lỗi tìm kiếm"))
                }
        }
    }

    private fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val catList = s.selectedCategoryIds.toList().takeIf { it.isNotEmpty() }
            discoveryRepository.searchUsers(
                query = s.query.ifBlank { null },
                categoryIds = catList,
                city = s.selectedCity?.ifBlank { null },
                minProficiency = s.minProficiency,
                maxProficiency = s.maxProficiency,
                type = s.selectedType,
                limit = 20,
                lastId = cursor
            )
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            usersList = it.usersList + result.users,
                            nextCursor = result.nextCursor
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoadingMore = false) }
                    _effect.send(DiscoveryEffect.ShowSnackbar(e.message ?: "Lỗi tải thêm"))
                }
        }
    }
}
