package com.skillexchange.app.presentation.profile.myprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.repository.IFriendRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.IRatingRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyProfileViewModel(
    private val profileRepository: IProfileRepository,
    private val skillRepository: ISkillRepository,
    private val ratingRepository: IRatingRepository,
    private val friendRepository: IFriendRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyProfileUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<MyProfileEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadData()
    }

    fun onIntent(intent: MyProfileIntent) {
        when (intent) {
            MyProfileIntent.LoadData -> loadData()
            MyProfileIntent.Refresh -> loadData()
            MyProfileIntent.EditProfile -> {
                viewModelScope.launch { _effect.send(MyProfileEffect.NavigateToEditProfile) }
            }
            MyProfileIntent.ManageSkills -> {
                viewModelScope.launch { _effect.send(MyProfileEffect.NavigateToManageSkills) }
            }
            MyProfileIntent.ViewFriends -> {
                viewModelScope.launch { _effect.send(MyProfileEffect.NavigateToFriends) }
            }
            MyProfileIntent.Logout -> logout()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val profileRes = profileRepository.getMyProfile()
            if (profileRes.isFailure) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = profileRes.exceptionOrNull()?.message ?: "Lỗi tải thông tin hồ sơ"
                    )
                }
                return@launch
            }

            val profile = profileRes.getOrNull()
            val skills = skillRepository.getUserSkills("me").getOrDefault(emptyList())

            val currentUserId = tokenManager.getUserId() ?: profile?.userId
            val reputation = currentUserId?.let { uid ->
                ratingRepository.getUserReputation(uid).getOrNull()
            }

            val friends = friendRepository.getFriends().getOrDefault(emptyList())
            val pending = friendRepository.getPendingRequests().getOrDefault(emptyList())

            _state.update {
                it.copy(
                    isLoading = false,
                    profile = profile,
                    skills = skills,
                    reputation = reputation,
                    friendsCount = friends.size,
                    pendingInvitationsCount = pending.size,
                    error = null
                )
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            tokenManager.clearSession()
            _effect.send(MyProfileEffect.NavigateToLogin)
        }
    }
}
