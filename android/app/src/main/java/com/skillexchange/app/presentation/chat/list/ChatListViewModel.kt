package com.skillexchange.app.presentation.chat.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.IChatRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListState(
    val isLoading: Boolean = true,
    val rooms: List<ChatRoom> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val currentUserId: String = ""
) {
    val filteredRooms: List<ChatRoom>
        get() = if (searchQuery.isBlank()) {
            rooms
        } else {
            val query = searchQuery.trim().lowercase()
            rooms.filter {
                it.getOtherPartyName(currentUserId).lowercase().contains(query) ||
                (it.lastMessage?.lowercase()?.contains(query) == true) ||
                it.skillOfferedName.lowercase().contains(query) ||
                it.skillWantedName.lowercase().contains(query)
            }
        }
}

sealed interface ChatListIntent {
    data class SearchQueryChanged(val query: String) : ChatListIntent
    data object Retry : ChatListIntent
    data class OpenChat(val chatId: String) : ChatListIntent
}

sealed interface ChatListEffect {
    data class NavigateToChat(val chatId: String) : ChatListEffect
}

class ChatListViewModel(
    private val chatRepository: IChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    private val _effect = Channel<ChatListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        val uid = tokenManager.getUserId() ?: ""
        _state.update { it.copy(currentUserId = uid) }
        observeChatRooms()
    }

    fun onIntent(intent: ChatListIntent) {
        when (intent) {
            is ChatListIntent.SearchQueryChanged -> {
                _state.update { it.copy(searchQuery = intent.query) }
            }
            is ChatListIntent.Retry -> {
                observeChatRooms()
            }
            is ChatListIntent.OpenChat -> {
                viewModelScope.launch {
                    _effect.send(ChatListEffect.NavigateToChat(intent.chatId))
                }
            }
        }
    }

    private fun observeChatRooms() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            chatRepository.getChatRoomsFlow()
                .catch { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Không thể tải danh sách cuộc trò chuyện"
                        )
                    }
                }
                .collect { rooms ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            rooms = rooms,
                            error = null
                        )
                    }
                }
        }
    }
}
