package com.skillexchange.app.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileDetailUiState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val skills: List<UserSkill> = emptyList(),
    val error: String? = null
)

sealed class ProfileDetailIntent {
    data class LoadUserProfile(val userId: String) : ProfileDetailIntent()
    object RequestExchange : ProfileDetailIntent()
}

sealed class ProfileDetailEffect {
    data class NavigateToBooking(val receiverId: String) : ProfileDetailEffect()
    data class ShowSnackbar(val message: String) : ProfileDetailEffect()
}

class ProfileDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: IProfileRepository,
    private val skillRepository: ISkillRepository
) : ViewModel() {

    private val targetUserId: String? = savedStateHandle["userId"]

    private val _state = MutableStateFlow(ProfileDetailUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<ProfileDetailEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        targetUserId?.let { loadUserProfile(it) }
    }

    fun onIntent(intent: ProfileDetailIntent) {
        when (intent) {
            is ProfileDetailIntent.LoadUserProfile -> loadUserProfile(intent.userId)
            is ProfileDetailIntent.RequestExchange -> {
                targetUserId?.let { userId ->
                    viewModelScope.launch {
                        _effect.send(ProfileDetailEffect.NavigateToBooking(userId))
                    }
                }
            }
        }
    }

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            launch {
                profileRepository.getUserProfile(userId)
                    .onSuccess { p -> _state.update { it.copy(profile = p) } }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Lỗi tải hồ sơ") } }
            }

            launch {
                skillRepository.getUserSkills(userId)
                    .onSuccess { s -> _state.update { it.copy(skills = s) } }
            }

            _state.update { it.copy(isLoading = false) }
        }
    }
}
