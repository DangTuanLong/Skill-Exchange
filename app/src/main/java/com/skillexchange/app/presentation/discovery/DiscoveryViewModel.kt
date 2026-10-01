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
    val selectedCategoryId: Int? = null,
    val selectedCity: String? = null,
    val minProficiency: Int? = null,
    val selectedType: String? = null,    // "HAVE" | "WANT" | null
    val usersList: List<UserDiscovery> = emptyList(),
    val categories: List<SkillCategory> = emptyList(),
    val isLoading: Boolean = false,
    val isFilterOpen: Boolean = false,
    val error: String? = null
)

// ─── Intent ───────────────────────────────────────────────────────────
sealed class DiscoveryIntent {
    data class QueryChanged(val value: String) : DiscoveryIntent()
    data class CategorySelected(val categoryId: Int?) : DiscoveryIntent()
    data class CitySelected(val city: String?) : DiscoveryIntent()
    data class MinProficiencySelected(val level: Int?) : DiscoveryIntent()
    data class TypeSelected(val type: String?) : DiscoveryIntent()
    object ToggleFilterSheet : DiscoveryIntent()
    object ResetFilters : DiscoveryIntent()
    object PerformSearch : DiscoveryIntent()
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
            is DiscoveryIntent.QueryChanged          -> _state.update { it.copy(query = intent.value) }
            is DiscoveryIntent.CategorySelected      -> {
                _state.update { it.copy(selectedCategoryId = if (it.selectedCategoryId == intent.categoryId) null else intent.categoryId) }
                performSearch()
            }
            is DiscoveryIntent.CitySelected          -> _state.update { it.copy(selectedCity = intent.city) }
            is DiscoveryIntent.MinProficiencySelected-> _state.update { it.copy(minProficiency = intent.level) }
            is DiscoveryIntent.TypeSelected          -> _state.update { it.copy(selectedType = intent.type) }
            is DiscoveryIntent.ToggleFilterSheet     -> _state.update { it.copy(isFilterOpen = !it.isFilterOpen) }
            is DiscoveryIntent.ResetFilters          -> {
                _state.update { it.copy(
                    query = "",
                    selectedCategoryId = null,
                    selectedCity = null,
                    minProficiency = null,
                    selectedType = null
                ) }
                performSearch()
            }
            is DiscoveryIntent.PerformSearch         -> performSearch()
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
            _state.update { it.copy(isLoading = true, error = null) }
            discoveryRepository.searchUsers(
                query = s.query.ifBlank { null },
                categoryId = s.selectedCategoryId,
                city = s.selectedCity?.ifBlank { null },
                minProficiency = s.minProficiency,
                type = s.selectedType,
                limit = 30,
                offset = 0
            )
                .onSuccess { users ->
                    _state.update { it.copy(isLoading = false, usersList = users) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Lỗi tìm kiếm") }
                    _effect.send(DiscoveryEffect.ShowSnackbar(e.message ?: "Lỗi tìm kiếm"))
                }
        }
    }
}
