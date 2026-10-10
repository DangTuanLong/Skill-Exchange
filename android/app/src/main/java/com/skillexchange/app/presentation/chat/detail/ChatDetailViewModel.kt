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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    val fallbackSkillWanted: String? = null,
    val isOtherUserTyping: Boolean = false
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
    data class SendAttachment(
        val fileBytes: ByteArray,
        val fileName: String,
        val mimeType: String,
        val isImage: Boolean
    ) : ChatDetailIntent
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
    val effect = _receiveEffect()

    private fun _receiveEffect() = _effect.receiveAsFlow()

    // Quản lý các tin nhắn local đang gửi hoặc thất bại
    private val optimisticMessages = mutableMapOf<String, ChatMessage>()
    private val failedMessageIds = mutableSetOf<String>()

    // Bộ nhớ đệm tệp đính kèm khi gửi để phục vụ thử lại (resend)
    private data class PendingAttachment(
        val fileBytes: ByteArray,
        val fileName: String,
        val mimeType: String,
        val isImage: Boolean,
        var uploadedUrl: String? = null
    )
    private val pendingAttachments = mutableMapOf<String, PendingAttachment>()

    // Chống vòng lặp mark-read: tập hợp ID đã gửi lệnh markAsRead
    private val pendingMarkReadIds = mutableSetOf<String>()

    // Quản lý typing indicator: chỉ ghi khi chuyển trạng thái (2 writes per burst), debounce 2s
    private var isCurrentlyTypingLocally = false
    private var typingDebounceJob: Job? = null
    private var observeTypingJob: Job? = null

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
                    handleTypingTransition(intent.text.isNotBlank())
                }
            }
            is ChatDetailIntent.SendMessage -> {
                sendMessage()
            }
            is ChatDetailIntent.SendAttachment -> {
                sendAttachment(intent.fileBytes, intent.fileName, intent.mimeType, intent.isImage)
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

    private fun handleTypingTransition(hasText: Boolean) {
        if (hasText) {
            if (!isCurrentlyTypingLocally) {
                isCurrentlyTypingLocally = true
                viewModelScope.launch {
                    chatRepository.setTypingStatus(chatId, true)
                }
            }
            typingDebounceJob?.cancel()
            typingDebounceJob = viewModelScope.launch {
                delay(2000L)
                if (isCurrentlyTypingLocally) {
                    isCurrentlyTypingLocally = false
                    chatRepository.setTypingStatus(chatId, false)
                }
            }
        } else {
            if (isCurrentlyTypingLocally) {
                typingDebounceJob?.cancel()
                isCurrentlyTypingLocally = false
                viewModelScope.launch {
                    chatRepository.setTypingStatus(chatId, false)
                }
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
                    if (room != null) {
                        observeOtherUserTypingIfNeeded(room)
                    }
                }
        }
    }

    private fun observeOtherUserTypingIfNeeded(room: ChatRoom) {
        if (observeTypingJob != null) return
        val uid = _state.value.currentUserId
        val otherUserId = room.participants.firstOrNull { it != uid }
            ?: if (room.senderId == uid) room.receiverId else room.senderId
        if (otherUserId.isNotBlank()) {
            observeTypingJob = viewModelScope.launch {
                chatRepository.observeOtherUserTyping(chatId, otherUserId)
                    .collect { isTyping ->
                        _state.update { it.copy(isOtherUserTyping = isTyping) }
                    }
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
                        pendingAttachments.remove(msg.id)
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

        // Chấm dứt trạng thái typing ngay khi bấm gửi
        if (isCurrentlyTypingLocally) {
            typingDebounceJob?.cancel()
            isCurrentlyTypingLocally = false
            viewModelScope.launch {
                chatRepository.setTypingStatus(chatId, false)
            }
        }

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

    private fun sendAttachment(fileBytes: ByteArray, fileName: String, mimeType: String, isImage: Boolean) {
        val currentRoom = _state.value.room
        if (currentRoom == null) {
            viewModelScope.launch {
                _effect.send(ChatDetailEffect.ShowSnackbar("Phòng chat đang khởi tạo, vui lòng chờ trong giây lát"))
            }
            return
        }

        if (currentRoom.status != "ACCEPTED") {
            viewModelScope.launch {
                _effect.send(ChatDetailEffect.ShowSnackbar("Lịch trao đổi đã kết thúc, không thể gửi tệp"))
            }
            return
        }

        val maxAllowedSize = if (isImage) 5L * 1024 * 1024 else 10L * 1024 * 1024
        if (fileBytes.size > maxAllowedSize) {
            val limitStr = if (isImage) "5MB" else "10MB"
            viewModelScope.launch {
                _effect.send(ChatDetailEffect.ShowSnackbar("Dung lượng tệp vượt quá giới hạn cho phép ($limitStr)"))
            }
            return
        }

        val participants = currentRoom.participants.takeIf { it.isNotEmpty() }
            ?: listOf(currentRoom.senderId, currentRoom.receiverId)

        val messageId = chatRepository.generateNewMessageId(chatId)
        val sendTime = System.currentTimeMillis()
        val type = if (isImage) "IMAGE" else "FILE"
        val content = if (isImage) "[Hình ảnh]" else "[Tệp] $fileName"

        val localMsg = ChatMessage(
            id = messageId,
            chatId = chatId,
            senderId = _state.value.currentUserId,
            content = content,
            type = type,
            createdAt = sendTime,
            readAt = null,
            participants = participants,
            isPending = true,
            isFailed = false,
            fileName = fileName,
            fileSize = fileBytes.size.toLong()
        )

        optimisticMessages[messageId] = localMsg
        pendingAttachments[messageId] = PendingAttachment(fileBytes, fileName, mimeType, isImage)

        _state.update {
            it.copy(
                messages = (it.messages + localMsg).distinctBy { m -> m.id }.sortedBy { m -> m.createdAt }
            )
        }

        viewModelScope.launch {
            _effect.send(ChatDetailEffect.ScrollToBottom)
        }

        dispatchAttachmentSend(messageId, fileBytes, fileName, mimeType, isImage, participants)
    }

    private fun dispatchAttachmentSend(
        messageId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        isImage: Boolean,
        participants: List<String>
    ) {
        viewModelScope.launch {
            val pending = pendingAttachments[messageId]
            val uploadedUrl: String
            if (pending?.uploadedUrl != null) {
                uploadedUrl = pending.uploadedUrl!!
            } else {
                val uploadResult = chatRepository.uploadAttachment(chatId, fileBytes, fileName, mimeType)
                if (uploadResult.isFailure) {
                    val err = uploadResult.exceptionOrNull()?.message ?: "Tải tệp lên thất bại"
                    markMessageFailed(messageId, err)
                    return@launch
                }
                uploadedUrl = uploadResult.getOrThrow().url
                pending?.uploadedUrl = uploadedUrl
            }

            val type = if (isImage) "IMAGE" else "FILE"
            val content = if (isImage) "[Hình ảnh]" else "[Tệp] $fileName"

            val sendResult = chatRepository.sendMessage(
                chatId = chatId,
                content = content,
                participants = participants,
                messageId = messageId,
                type = type,
                fileUrl = uploadedUrl,
                fileName = fileName,
                fileSize = fileBytes.size.toLong()
            )

            sendResult.onSuccess {
                failedMessageIds.remove(messageId)
                pendingAttachments.remove(messageId)
            }.onFailure { e ->
                markMessageFailed(messageId, e.message ?: "Lỗi khi lưu tin nhắn")
            }
        }
    }

    private suspend fun markMessageFailed(messageId: String, errorMsg: String) {
        failedMessageIds.add(messageId)
        optimisticMessages.remove(messageId)

        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(isFailed = true, isPending = false) else it
                }
            )
        }
        _effect.send(ChatDetailEffect.ShowSnackbar("Gửi thất bại: $errorMsg"))
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

        val pending = pendingAttachments[messageId]
        if (pending != null) {
            dispatchAttachmentSend(
                messageId,
                pending.fileBytes,
                pending.fileName,
                pending.mimeType,
                pending.isImage,
                participants
            )
        } else {
            dispatchSend(messageId, targetMsg.content, participants)
        }
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
                markMessageFailed(messageId, e.message ?: "Lỗi kết nối")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        typingDebounceJob?.cancel()
        if (isCurrentlyTypingLocally) {
            isCurrentlyTypingLocally = false
            CoroutineScope(Dispatchers.IO).launch {
                chatRepository.setTypingStatus(chatId, false)
            }
        }
    }
}
