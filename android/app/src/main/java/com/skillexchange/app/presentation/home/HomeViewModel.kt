package com.skillexchange.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.repository.IChatRepository
import com.skillexchange.app.domain.repository.IDiscoveryRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab {
    MATCHING,    // "Gợi ý phù hợp"
    ALL_USERS    // "Tất cả đối tác"
}

// ─── State ────────────────────────────────────────────────────────────
data class HomeUiState(
    val query: String = "",
    val selectedCategoryIds: Set<Int> = emptySet(),
    val selectedCity: String? = null,
    val minProficiency: Int? = null,
    val maxProficiency: Int? = null,
    val selectedType: String? = null,    // "HAVE" | "WANT" | null
    val selectedTab: HomeTab = HomeTab.MATCHING,

    val categories: List<SkillCategory> = emptyList(),
    val discoveryUsers: List<UserDiscovery> = emptyList(),
    val matchingSuggestions: List<UserDiscovery> = emptyList(),

    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val isFilterOpen: Boolean = false,
    val unreadChatCount: Int = 0,
    val error: String? = null
) {
    val displayedUsers: List<UserDiscovery>
        get() = if (selectedTab == HomeTab.MATCHING) matchingSuggestions else discoveryUsers

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
sealed class HomeIntent {
    data class QueryChanged(val value: String) : HomeIntent()
    data class ToggleCategory(val categoryId: Int) : HomeIntent()
    object ClearCategories : HomeIntent()
    data class CitySelected(val city: String?) : HomeIntent()
    data class MinProficiencySelected(val level: Int?) : HomeIntent()
    data class MaxProficiencySelected(val level: Int?) : HomeIntent()
    data class TypeSelected(val type: String?) : HomeIntent()
    object ToggleFilterSheet : HomeIntent()
    object ResetFilters : HomeIntent()
    data class SwitchTab(val tab: HomeTab) : HomeIntent()
    object PerformSearch : HomeIntent()
    object LoadMore : HomeIntent()
    object Retry : HomeIntent()
}

// ─── Effect ───────────────────────────────────────────────────────────
sealed class HomeEffect {
    data class ShowSnackbar(val message: String) : HomeEffect()
    data class NavigateToProfileDetail(val userId: String) : HomeEffect()
}

class HomeViewModel(
    private val discoveryRepository: IDiscoveryRepository,
    private val skillRepository: ISkillRepository,
    private val chatRepository: IChatRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<HomeEffect>()
    val effect = _effect.receiveAsFlow()

    private var debounceJob: Job? = null

    init {
        loadCategories()
        loadMatchingSuggestions()
        performSearch(resetCursor = true)
        observeUnreadChatCount()
    }

    private fun observeUnreadChatCount() {
        val repo = chatRepository ?: return
        viewModelScope.launch {
            repo.getChatRoomsFlow()
                .catch { /* ignore flow errors in unread count */ }
                .collect { rooms ->
                    val totalUnread = rooms.sumOf { it.unreadCount }
                    _state.update { it.copy(unreadChatCount = totalUnread) }
                }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.QueryChanged -> {
                _state.update { it.copy(query = intent.value) }
                debounceJob?.cancel()
                debounceJob = viewModelScope.launch {
                    delay(300L)
                    if (intent.value.isNotBlank() && _state.value.selectedTab == HomeTab.MATCHING) {
                        _state.update { it.copy(selectedTab = HomeTab.ALL_USERS) }
                    }
                    performSearch(resetCursor = true)
                }
            }

            is HomeIntent.ToggleCategory -> {
                _state.update { current ->
                    val newSelection = if (current.selectedCategoryIds.contains(intent.categoryId)) {
                        current.selectedCategoryIds - intent.categoryId
                    } else {
                        current.selectedCategoryIds + intent.categoryId
                    }
                    val targetTab = if (current.selectedTab == HomeTab.MATCHING && newSelection.isNotEmpty()) {
                        HomeTab.ALL_USERS
                    } else {
                        current.selectedTab
                    }
                    current.copy(selectedCategoryIds = newSelection, selectedTab = targetTab)
                }
                performSearch(resetCursor = true)
            }

            is HomeIntent.ClearCategories -> {
                _state.update { it.copy(selectedCategoryIds = emptySet()) }
                performSearch(resetCursor = true)
            }

            is HomeIntent.CitySelected -> {
                _state.update { it.copy(selectedCity = intent.city) }
            }

            is HomeIntent.MinProficiencySelected -> {
                _state.update { it.copy(minProficiency = intent.level) }
            }

            is HomeIntent.MaxProficiencySelected -> {
                _state.update { it.copy(maxProficiency = intent.level) }
            }

            is HomeIntent.TypeSelected -> {
                _state.update { it.copy(selectedType = intent.type) }
            }

            is HomeIntent.ToggleFilterSheet -> {
                _state.update { it.copy(isFilterOpen = !it.isFilterOpen) }
            }

            is HomeIntent.ResetFilters -> {
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
                performSearch(resetCursor = true)
            }

            is HomeIntent.SwitchTab -> {
                val newTab = intent.tab
                _state.update { it.copy(selectedTab = newTab) }
                if (newTab == HomeTab.MATCHING && _state.value.matchingSuggestions.isEmpty()) {
                    loadMatchingSuggestions()
                } else if (newTab == HomeTab.ALL_USERS && _state.value.discoveryUsers.isEmpty()) {
                    performSearch(resetCursor = true)
                }
            }

            is HomeIntent.PerformSearch -> {
                debounceJob?.cancel()
                if (_state.value.selectedTab == HomeTab.MATCHING) {
                    _state.update { it.copy(selectedTab = HomeTab.ALL_USERS) }
                }
                performSearch(resetCursor = true)
            }

            is HomeIntent.LoadMore -> {
                if (_state.value.selectedTab == HomeTab.ALL_USERS) {
                    loadMore()
                }
            }

            is HomeIntent.Retry -> {
                if (_state.value.selectedTab == HomeTab.MATCHING) {
                    loadMatchingSuggestions()
                } else {
                    performSearch(resetCursor = true)
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            skillRepository.getCategories().onSuccess { cats ->
                _state.update { it.copy(categories = cats) }
            }
        }
    }

    fun loadMatchingSuggestions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            discoveryRepository.getMatchingSuggestions(limit = 20)
                .onSuccess { suggestions ->
                    _state.update {
                        it.copy(
                            matchingSuggestions = suggestions,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { ex ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = ex.message ?: "Không thể tải gợi ý phù hợp"
                        )
                    }
                }
        }
    }

    fun performSearch(resetCursor: Boolean = true) {
        val s = _state.value
        val lastId = if (resetCursor) null else s.nextCursor

        viewModelScope.launch {
            if (resetCursor) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else {
                _state.update { it.copy(isLoadingMore = true) }
            }

            val categoryList = s.selectedCategoryIds.toList().takeIf { it.isNotEmpty() }
            discoveryRepository.searchUsers(
                query = s.query.takeIf { it.isNotBlank() },
                categoryIds = categoryList,
                city = s.selectedCity,
                minProficiency = s.minProficiency,
                maxProficiency = s.maxProficiency,
                type = s.selectedType,
                limit = 20,
                lastId = lastId
            ).onSuccess { result ->
                _state.update { current ->
                    val newUsers = if (resetCursor) result.users else current.discoveryUsers + result.users
                    current.copy(
                        discoveryUsers = newUsers,
                        nextCursor = result.nextCursor,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null
                    )
                }
            }.onFailure { ex ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = ex.message ?: "Không thể kết nối máy chủ"
                    )
                }
            }
        }
    }

    private fun loadMore() {
        val s = _state.value
        if (s.nextCursor != null && !s.isLoading && !s.isLoadingMore) {
            performSearch(resetCursor = false)
        }
    }
}
