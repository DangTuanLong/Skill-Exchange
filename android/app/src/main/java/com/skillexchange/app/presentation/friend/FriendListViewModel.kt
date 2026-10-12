package com.skillexchange.app.presentation.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.repository.IFriendRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendListViewModel(
    private val friendRepository: IFriendRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FriendListState())
    val state = _state.asStateFlow()

    private val _effect = Channel<FriendListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadData()
    }

    fun onIntent(intent: FriendListIntent) {
        when (intent) {
            is FriendListIntent.SelectTab -> _state.update { it.copy(selectedTab = intent.tab) }
            FriendListIntent.LoadData -> loadData()
            FriendListIntent.Retry -> loadData()
            is FriendListIntent.AcceptInvitation -> acceptInvitation(intent.friendshipId)
            is FriendListIntent.RejectInvitation -> rejectInvitation(intent.friendshipId)
            is FriendListIntent.RemoveFriend -> removeFriend(intent.friendshipId)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val friendsRes = friendRepository.getFriends()
            val invitationsRes = friendRepository.getPendingRequests()

            if (friendsRes.isSuccess && invitationsRes.isSuccess) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        friends = friendsRes.getOrDefault(emptyList()),
                        invitations = invitationsRes.getOrDefault(emptyList()),
                        error = null
                    )
                }
            } else {
                val err = friendsRes.exceptionOrNull()?.message ?: invitationsRes.exceptionOrNull()?.message ?: "Lỗi tải danh sách bạn bè"
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = err
                    )
                }
            }
        }
    }

    private fun acceptInvitation(friendshipId: String) {
        viewModelScope.launch {
            friendRepository.acceptFriendRequest(friendshipId)
                .onSuccess {
                    _effect.send(FriendListEffect.ShowSnackbar("Đã chấp nhận lời mời kết bạn!"))
                    loadData()
                }
                .onFailure { err ->
                    _effect.send(FriendListEffect.ShowSnackbar(err.message ?: "Không thể chấp nhận lời mời"))
                }
        }
    }

    private fun rejectInvitation(friendshipId: String) {
        viewModelScope.launch {
            friendRepository.rejectFriendRequest(friendshipId)
                .onSuccess {
                    _effect.send(FriendListEffect.ShowSnackbar("Đã từ chối lời mời kết bạn."))
                    loadData()
                }
                .onFailure { err ->
                    _effect.send(FriendListEffect.ShowSnackbar(err.message ?: "Không thể từ chối lời mời"))
                }
        }
    }

    private fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            friendRepository.removeFriendship(friendshipId)
                .onSuccess {
                    _effect.send(FriendListEffect.ShowSnackbar("Đã hủy kết bạn."))
                    loadData()
                }
                .onFailure { err ->
                    _effect.send(FriendListEffect.ShowSnackbar(err.message ?: "Không thể hủy kết bạn"))
                }
        }
    }
}
