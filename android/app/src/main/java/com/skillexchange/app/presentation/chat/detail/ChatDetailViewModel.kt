package com.skillexchange.app.presentation.chat.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.IChatRepository
import com.skillexchange.app.domain.repository.IExchangeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatDetailState(
    val isLoading: Boolean = true,
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val error: String? = null,
    val currentUserId: String = "",
    val fallbackOtherName: String? = null,
    val fallbackOtherAvatar: String? = null,
    val fallbackSkillOffered: String? = null,
    val fallbackSkillWanted: String? = null
) {
    val isReadOnly: Boolean get() = room?.isReadOnly ?: false

    fun getDisplayName(currentUserId: String): String =
        room?.getOtherPartyName(currentUserId)
            ?: fallbackOtherName?.takeIf { it.isNotBlank() }
            ?: "Đang tải..."

    fun getDisplayAvatar(currentUserId: String): String? =
        room?.getOtherPartyAvatar(currentUserId)
            ?: fallbackOtherAvatar

    val displaySkillOffered: String
        get() = room?.skillOfferedName
            ?: fallbackSkillOffered
            ?: ""

    val displaySkillWanted: String
        get() = room?.skillWantedName
            ?: fallbackSkillWanted
            ?: ""
}

sealed interface ChatDetailIntent {
    data class InputTextChanged(val text: String) : ChatDetailIntent
    data object SendMessage : ChatDetailIntent
    data class ResendMessage(val messageId: String) : ChatDetailIntent
    data object NavigateToBookingDetail : ChatDetailIntent
    data object Retry : ChatDetailIntent
}

sealed interface ChatDetailEffect {
    data class ShowSnackbar(val message: String) : ChatDetailEffect
    data class NavigateToBooking(val exchangeId: String) : ChatDetailEffect
    data object ScrollToBottom : ChatDetailEffect
}

class ChatDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: IChatRepository,
    private val tokenManager: TokenManager,
    private val exchangeRepository: IExchangeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ChatDetailViewModel"
    }

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(ChatDetailState())
    val state: StateFlow<ChatDetailState> = _state.asStateFlow()

    private val _effect = Channel<ChatDetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    // Quản lý các tin nhắn local đang gửi hoặc thất bại
    private val optimisticMessages = mutableMapOf<String, ChatMessage>()
    private val failedMessageIds = mutableSetOf<String>()

    // Chống vòng lặp mark-read: tập hợp ID đã gửi lệnh markAsRead
    private val pendingMarkReadIds = mutableSetOf<String>()

    init {
        val uid = tokenManager.getUserId() ?: ""
        _state.update { it.copy(currentUserId = uid) }
        loadExchangeFallback()
        observeRoom()
        observeMessages()
    }

    private fun loadExchangeFallback() {
        viewModelScope.launch {
            exchangeRepository.getExchangeRequest(chatId).onSuccess { exchange ->
                val uid = _state.value.currentUserId
                _state.update {
                    it.copy(
                        fallbackOtherName = exchange.otherUserName(uid),
                        fallbackOtherAvatar = exchange.otherUserAvatar(uid),
                        fallbackSkillOffered = exchange.skillOfferedName,
                        fallbackSkillWanted = exchange.skillWantedName
                    )
                }
            }
        }
    }

    fun onIntent(intent: ChatDetailIntent) {
        when (intent) {
            is ChatDetailIntent.InputTextChanged -> {
                if (intent.text.length <= 1000) {
                    _state.update { it.copy(inputText = intent.text) }
                }
            }
            is ChatDetailIntent.SendMessage -> {
                sendMessage()
            }
            is ChatDetailIntent.ResendMessage -> {
                resendMessage(intent.messageId)
            }
            is ChatDetailIntent.NavigateToBookingDetail -> {
                viewModelScope.launch {
                    _effect.send(ChatDetailEffect.NavigateToBooking(chatId))
                }
            }
            is ChatDetailIntent.Retry -> {
                _state.update { it.copy(isLoading = true, error = null) }
                loadExchangeFallback()
                observeRoom()
                observeMessages()
            }
        }
    }

    private fun observeRoom() {
        viewModelScope.launch {
            chatRepository.getChatRoom(chatId)
                .catch { e ->
                    _state.update { it.copy(error = e.message) }
                }
                .collect { room ->
                    _state.update { it.copy(room = room) }
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.getMessagesFlow(chatId, limit = 30L)
                .catch { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Không thể tải tin nhắn"
                        )
                    }
                }
                .collect { firestoreMessages ->
                    val now = System.currentTimeMillis()
                    Log.d("ChatPerf", "SNAPSHOT_RECEIVED at: $now, count: ${firestoreMessages.size}")

                    // Xoá các tin optimistic nếu đã có trên Firestore snapshot
                    firestoreMessages.forEach { msg ->
                        optimisticMessages.remove(msg.id)
                        if (!msg.isPending) {
                            failedMessageIds.remove(msg.id)
                        }
                    }

                    // Hợp nhất: Firestore messages + tin local optimistic chưa lên snapshot
                    val mergedList = (firestoreMessages + optimisticMessages.values)
                        .distinctBy { it.id }
                        .map { msg ->
                            if (msg.id in failedMessageIds) msg.copy(isFailed = true, isPending = false)
                            else msg
                        }
                        .sortedBy { it.createdAt }

                    val uid = _state.value.currentUserId
                    _state.update {
                        it.copy(
                            isLoading = false,
                            messages = mergedList,
                            error = null
                        )
                    }

                    // Batch mark-as-read: chỉ các tin chưa đọc từ đối phương và chưa gửi batch
                    val unreadIds = firestoreMessages
                        .filter { it.senderId != uid && it.readAt == null && it.id !in pendingMarkReadIds }
                        .map { it.id }

                    if (unreadIds.isNotEmpty()) {
                        pendingMarkReadIds.addAll(unreadIds)
                        launch {
                            chatRepository.markMessagesAsRead(chatId, unreadIds)
                        }
                    }
                }
        }
    }

    private fun sendMessage() {
        val content = _state.value.inputText.trim()
        if (content.isBlank()) return

        // 1. Kiểm tra phòng chat trong bộ nhớ (không gọi mạng roomRef.get())
        val currentRoom = _state.value.room
        if (currentRoom == null) {
            viewModelScope.launch {
                _effect.send(ChatDetailEffect.ShowSnackbar("Phòng chat đang khởi tạo, vui lòng chờ trong giây lát"))
            }
            return
        }

        if (currentRoom.status != "ACCEPTED") {
            viewModelScope.launch {
                _effect.send(ChatDetailEffect.ShowSnackbar("Lịch trao đổi đã kết thúc, không thể gửi tin nhắn"))
            }
            return
        }

        val participants = currentRoom.participants.takeIf { it.isNotEmpty() }
            ?: listOf(currentRoom.senderId, currentRoom.receiverId)

        val sendTime = System.currentTimeMillis()
        val messageId = chatRepository.generateNewMessageId(chatId)
        Log.d("ChatPerf", "SEND_CLICK at: $sendTime, messageId: $messageId")

        // 2. Optimistic UI: Tạo tin nhắn cục bộ, xóa ô nhập ngay lập tức
        val localMsg = ChatMessage(
            id = messageId,
            chatId = chatId,
            senderId = _state.value.currentUserId,
            content = content,
            type = "TEXT",
            createdAt = sendTime,
            readAt = null,
            participants = participants,
            isPending = true,
            isFailed = false
        )

        optimisticMessages[messageId] = localMsg
        _state.update {
            it.copy(
                inputText = "",
                messages = (it.messages + localMsg).distinctBy { m -> m.id }.sortedBy { m -> m.createdAt }
            )
        }

        viewModelScope.launch {
            _effect.send(ChatDetailEffect.ScrollToBottom)
        }

        // 3. Ghi Firestore trong coroutine nền (không chặn UI)
        dispatchSend(messageId, content, participants)
    }

    private fun resendMessage(messageId: String) {
        val targetMsg = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val currentRoom = _state.value.room ?: return
        val participants = currentRoom.participants.takeIf { it.isNotEmpty() }
            ?: listOf(currentRoom.senderId, currentRoom.receiverId)

        failedMessageIds.remove(messageId)
        val updated = targetMsg.copy(isFailed = false, isPending = true)
        optimisticMessages[messageId] = updated

        _state.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) updated else it })
        }

        dispatchSend(messageId, targetMsg.content, participants)
    }

    private fun dispatchSend(messageId: String, content: String, participants: List<String>) {
        viewModelScope.launch {
            val result = chatRepository.sendMessage(
                chatId = chatId,
                content = content,
                participants = participants,
                messageId = messageId
            )

            result.onSuccess {
                Log.d("ChatPerf", "SERVER_COMMIT_SUCCESS for msgId: $messageId at ${System.currentTimeMillis()}")
                failedMessageIds.remove(messageId)
            }.onFailure { e ->
                Log.e(TAG, "Lỗi khi gửi tin nhắn $messageId: ${e.message}")
                failedMessageIds.add(messageId)
                optimisticMessages.remove(messageId)

                _state.update { state ->
                    state.copy(
                        messages = state.messages.map {
                            if (it.id == messageId) it.copy(isFailed = true, isPending = false) else it
                        }
                    )
                }
                _effect.send(ChatDetailEffect.ShowSnackbar("Gửi thất bại: ${e.message ?: "Lỗi kết nối"}"))
            }
        }
    }
}
