package com.skillexchange.app.presentation.chat.detail

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.chat.ChatAttachmentDataDto
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.IChatRepository
import com.skillexchange.app.domain.repository.IExchangeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_me") }

    private val sampleRoom = ChatRoom(
        id = "chat_123",
        exchangeId = "chat_123",
        senderId = "user_me",
        receiverId = "user_partner",
        senderName = "Tôi",
        receiverName = "Đối tác",
        senderAvatarUrl = null,
        receiverAvatarUrl = null,
        skillOfferedName = "Kotlin",
        skillWantedName = "Design",
        status = "ACCEPTED",
        lastMessage = "Chào bạn!",
        lastMessageAt = 1000L,
        lastSenderId = "user_partner",
        unreadCount = 1,
        participants = listOf("user_me", "user_partner")
    )

    private val sampleMessages = listOf(
        ChatMessage(
            id = "msg_1",
            chatId = "chat_123",
            senderId = "user_partner",
            content = "Chào bạn, hôm nay có thể học không?",
            type = "TEXT",
            createdAt = 1000L,
            readAt = null,
            participants = listOf("user_me", "user_partner")
        )
    )

    private val roomFlow = MutableSharedFlow<ChatRoom?>(replay = 1)
    private val messagesFlow = MutableSharedFlow<List<ChatMessage>>(replay = 1)
    private val markedAsReadIds = mutableListOf<String>()
    private var sentMessageContent: String? = null
    private var lastSentMessageId: String? = null
    private var lastSentParticipants: List<String>? = null
    private var lastSentType: String? = null
    private var lastSentFileUrl: String? = null
    private var lastSentFileName: String? = null
    private var lastSentFileSize: Long? = null
    private val typingStatusHistory = mutableListOf<Boolean>()
    private val otherUserTypingFlow = MutableSharedFlow<Boolean>(replay = 1)
    private var uploadAttachmentResult: Result<ChatAttachmentDataDto> = Result.success(
        ChatAttachmentDataDto(
            url = "https://pub-r2.skillexchange.dev/chats/chat_123/test.jpg",
            type = "IMAGE",
            fileName = "test.jpg",
            fileSize = 1024L
        )
    )
    private var generatedMessageIdCounter = 0
    private var sendMessageResult: Result<String> = Result.success("msg_generated_1")

    private val fakeChatRepository = object : IChatRepository {
        override fun getChatRoomsFlow(): Flow<List<ChatRoom>> = flowOf(emptyList())
        override fun getChatRoom(chatId: String): Flow<ChatRoom?> = roomFlow.asSharedFlow()
        override fun getMessagesFlow(chatId: String, limit: Long): Flow<List<ChatMessage>> = messagesFlow.asSharedFlow()

        override fun generateNewMessageId(chatId: String): String {
            generatedMessageIdCounter++
            return "msg_generated_$generatedMessageIdCounter"
        }

        override suspend fun sendMessage(
            chatId: String,
            content: String,
            participants: List<String>,
            messageId: String,
            type: String,
            fileUrl: String?,
            fileName: String?,
            fileSize: Long?
        ): Result<String> {
            sentMessageContent = content
            lastSentMessageId = messageId
            lastSentParticipants = participants
            lastSentType = type
            lastSentFileUrl = fileUrl
            lastSentFileName = fileName
            lastSentFileSize = fileSize
            return sendMessageResult
        }

        override suspend fun uploadAttachment(
            chatId: String,
            fileBytes: ByteArray,
            fileName: String,
            mimeType: String
        ): Result<ChatAttachmentDataDto> = uploadAttachmentResult

        override suspend fun setTypingStatus(chatId: String, isTyping: Boolean): Result<Unit> {
            typingStatusHistory.add(isTyping)
            return Result.success(Unit)
        }

        override fun observeOtherUserTyping(chatId: String, otherUserId: String): Flow<Boolean> =
            otherUserTypingFlow.asSharedFlow()

        override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit> {
            markedAsReadIds.addAll(messageIds)
            return Result.success(Unit)
        }
    }

    private var exchangeRequestResult: Result<ExchangeRequest> = Result.failure(NoSuchElementException())

    private val fakeExchangeRepository = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> =
            Result.failure(NotImplementedError())
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> =
            Result.success(emptyList())
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> =
            Result.success(emptyList())
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> =
            exchangeRequestResult
        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> =
            Result.failure(NotImplementedError())
        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> =
            Result.failure(NotImplementedError())
        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> =
            Result.failure(NotImplementedError())
        override suspend fun completeRequest(id: String): Result<ExchangeRequest> =
            Result.failure(NotImplementedError())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        markedAsReadIds.clear()
        sentMessageContent = null
        lastSentMessageId = null
        lastSentParticipants = null
        lastSentType = null
        lastSentFileUrl = null
        lastSentFileName = null
        lastSentFileSize = null
        typingStatusHistory.clear()
        uploadAttachmentResult = Result.success(
            ChatAttachmentDataDto(
                url = "https://pub-r2.skillexchange.dev/chats/chat_123/test.jpg",
                type = "IMAGE",
                fileName = "test.jpg",
                fileSize = 1024L
            )
        )
        generatedMessageIdCounter = 0
        sendMessageResult = Result.success("msg_generated_1")
        exchangeRequestResult = Result.failure(NoSuchElementException())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads room and messages, and automatically marks unread incoming messages as read`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(sampleRoom, state.room)
        assertEquals(1, state.messages.size)
        assertFalse(state.isReadOnly)

        // Verifies auto mark read of unread incoming message
        assertTrue(markedAsReadIds.contains("msg_1"))
    }

    @Test
    fun `fallback partner details are loaded from exchange repository when available`() = runTest(testDispatcher) {
        val sampleExchange = ExchangeRequest(
            id = "chat_123",
            senderId = "user_partner",
            receiverId = "user_me",
            senderName = "Lan Anh",
            senderAvatarUrl = "https://example.com/lananh.jpg",
            receiverName = "Tôi",
            receiverAvatarUrl = null,
            skillOfferedId = 1,
            skillOfferedName = "Nấu ăn",
            skillWantedId = 2,
            skillWantedName = "Guitar",
            status = ExchangeStatus.ACCEPTED,
            durationMinutes = 60,
            meetingMode = MeetingMode.ONLINE,
            scheduledAt = "2026-10-10T10:00:00",
            createdAt = "2026-10-01T10:00:00",
            updatedAt = "2026-10-01T10:00:00"
        )
        exchangeRequestResult = Result.success(sampleExchange)

        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Lan Anh", state.getDisplayName("user_me"))
        assertEquals("https://example.com/lananh.jpg", state.getDisplayAvatar("user_me"))
        assertEquals("Nấu ăn", state.displaySkillOffered)
        assertEquals("Guitar", state.displaySkillWanted)
    }

    @Test
    fun `SendMessage clears input text immediately, adds optimistic pending message, and emits ScrollToBottom`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        var effectReceived: ChatDetailEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Mình rảnh lúc 14h nhé!"))
        assertEquals("Mình rảnh lúc 14h nhé!", viewModel.state.value.inputText)

        // Bấm gửi: ô nhập phải xóa ngay lập tức
        viewModel.onIntent(ChatDetailIntent.SendMessage)

        assertEquals("", viewModel.state.value.inputText)
        val optimisticMsg = viewModel.state.value.messages.find { it.content == "Mình rảnh lúc 14h nhé!" }
        assertNotNull(optimisticMsg)
        assertEquals("msg_generated_1", optimisticMsg!!.id)
        assertTrue(optimisticMsg.isPending)
        assertFalse(optimisticMsg.isFailed)

        advanceUntilIdle()

        assertEquals("Mình rảnh lúc 14h nhé!", sentMessageContent)
        assertEquals("msg_generated_1", lastSentMessageId)
        assertEquals(listOf("user_me", "user_partner"), lastSentParticipants)
        assertEquals(ChatDetailEffect.ScrollToBottom, effectReceived)

        job.cancel()
    }

    @Test
    fun `When firestore snapshot emits the sent message, pending status is cleared`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Chào bạn"))
        viewModel.onIntent(ChatDetailIntent.SendMessage)
        advanceUntilIdle()

        // Firestore snapshot đẩy tin nhắn về (isPending = false)
        val serverMsg = ChatMessage(
            id = "msg_generated_1",
            chatId = "chat_123",
            senderId = "user_me",
            content = "Chào bạn",
            type = "TEXT",
            createdAt = System.currentTimeMillis(),
            readAt = null,
            participants = listOf("user_me", "user_partner"),
            isPending = false,
            isFailed = false
        )
        messagesFlow.emit(sampleMessages + serverMsg)
        advanceUntilIdle()

        val found = viewModel.state.value.messages.find { it.id == "msg_generated_1" }
        assertNotNull(found)
        assertFalse(found!!.isPending)
        assertFalse(found.isFailed)
    }

    @Test
    fun `SendMessage fails due to rules or network - marks message as failed and shows snackbar`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        sendMessageResult = Result.failure(Exception("PERMISSION_DENIED"))

        var effectReceived: ChatDetailEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Tin nhắn sẽ bị lỗi"))
        viewModel.onIntent(ChatDetailIntent.SendMessage)
        advanceUntilIdle()

        val failedMsg = viewModel.state.value.messages.find { it.content == "Tin nhắn sẽ bị lỗi" }
        assertNotNull(failedMsg)
        assertTrue(failedMsg!!.isFailed)
        assertFalse(failedMsg.isPending)

        assertTrue(effectReceived is ChatDetailEffect.ShowSnackbar)
        assertTrue((effectReceived as ChatDetailEffect.ShowSnackbar).message.contains("PERMISSION_DENIED"))

        job.cancel()
    }

    @Test
    fun `ResendMessage reuses existing messageId without duplicating messages`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        // Lần đầu gửi lỗi
        sendMessageResult = Result.failure(Exception("NETWORK_ERROR"))
        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Thử gửi lại"))
        viewModel.onIntent(ChatDetailIntent.SendMessage)
        advanceUntilIdle()

        val failedId = "msg_generated_1"
        assertEquals(2, viewModel.state.value.messages.size)
        assertTrue(viewModel.state.value.messages.find { it.id == failedId }!!.isFailed)

        // Lần 2 thử lại thành công
        sendMessageResult = Result.success(failedId)
        viewModel.onIntent(ChatDetailIntent.ResendMessage(failedId))

        // Kiểm tra ngay: trạng thái chuyển sang isPending=true, isFailed=false và không nhân đôi số lượng
        val resendingMsg = viewModel.state.value.messages.find { it.id == failedId }
        assertNotNull(resendingMsg)
        assertTrue(resendingMsg!!.isPending)
        assertFalse(resendingMsg.isFailed)
        assertEquals(2, viewModel.state.value.messages.size)

        advanceUntilIdle()

        // Phải dùng lại đúng messageId cũ, không sinh id mới
        assertEquals(failedId, lastSentMessageId)
        assertEquals(1, generatedMessageIdCounter) // counter không tăng thêm
    }

    @Test
    fun `SendMessage does nothing and keeps input text when room is read only`() = runTest(testDispatcher) {
        val completedRoom = sampleRoom.copy(status = "COMPLETED")
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        roomFlow.emit(completedRoom)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isReadOnly)

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Tin nhắn sau khi kết thúc"))
        viewModel.onIntent(ChatDetailIntent.SendMessage)
        advanceUntilIdle()

        assertEquals(null, sentMessageContent)
        // Ô nhập vẫn được giữ nguyên để người dùng không mất dữ liệu
        assertEquals("Tin nhắn sau khi kết thúc", viewModel.state.value.inputText)
    }

    @Test
    fun `SendMessage shows snackbar and keeps input text when room is null`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        var effectReceived: ChatDetailEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Chờ khởi tạo phòng"))
        viewModel.onIntent(ChatDetailIntent.SendMessage)
        advanceUntilIdle()

        assertEquals(null, sentMessageContent)
        assertEquals("Chờ khởi tạo phòng", viewModel.state.value.inputText)
        assertTrue(effectReceived is ChatDetailEffect.ShowSnackbar)

        job.cancel()
    }

    @Test
    fun `NavigateToBookingDetail emits NavigateToBooking effect`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        var effectReceived: ChatDetailEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        viewModel.onIntent(ChatDetailIntent.NavigateToBookingDetail)
        advanceUntilIdle()

        assertNotNull(effectReceived)
        assertTrue(effectReceived is ChatDetailEffect.NavigateToBooking)
        assertEquals("chat_123", (effectReceived as ChatDetailEffect.NavigateToBooking).exchangeId)

        job.cancel()
    }

    @Test
    fun `typing status triggers setTypingStatus true on start typing and does not repeat per keystroke`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        viewModel.onIntent(ChatDetailIntent.InputTextChanged("A"))
        testScheduler.runCurrent()
        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Ab"))
        testScheduler.runCurrent()
        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Abc"))
        testScheduler.runCurrent()

        // Phải chỉ ghi true đúng 1 lần khi bắt đầu gõ, không ghi lặp lại mỗi ký tự
        assertEquals(listOf(true), typingStatusHistory)

        // Sau 2s không gõ nữa -> chuyển sang false
        testScheduler.advanceTimeBy(2001L)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), typingStatusHistory)
    }

    @Test
    fun `typing status triggers false after 2s debounce or when text is cleared`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        // Bắt đầu gõ
        viewModel.onIntent(ChatDetailIntent.InputTextChanged("Xin chào"))
        testScheduler.runCurrent()
        assertEquals(listOf(true), typingStatusHistory)

        // Chưa đủ 2s (1000ms) -> vẫn là [true]
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(listOf(true), typingStatusHistory)

        // Đủ 2s debounce -> chuyển trạng thái sang false
        testScheduler.advanceTimeBy(1001L)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), typingStatusHistory)

        // Gõ tiếp rồi xóa trắng ngay -> lập tức chuyển sang false
        viewModel.onIntent(ChatDetailIntent.InputTextChanged("H"))
        testScheduler.runCurrent()
        assertEquals(listOf(true, false, true), typingStatusHistory)

        viewModel.onIntent(ChatDetailIntent.InputTextChanged(""))
        testScheduler.runCurrent()
        assertEquals(listOf(true, false, true, false), typingStatusHistory)
    }

    @Test
    fun `observing other user typing updates isOtherUserTyping in state`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)

        roomFlow.emit(sampleRoom)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOtherUserTyping)

        // Đối tác bắt đầu gõ
        otherUserTypingFlow.emit(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isOtherUserTyping)

        // Đối tác dừng gõ
        otherUserTypingFlow.emit(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOtherUserTyping)
    }

    @Test
    fun `SendAttachment adds optimistic message, uploads to R2 and sends with attachment metadata`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        messagesFlow.emit(sampleMessages)
        advanceUntilIdle()

        val fakeBytes = ByteArray(100) { 1 }
        viewModel.onIntent(
            ChatDetailIntent.SendAttachment(
                fileBytes = fakeBytes,
                fileName = "avatar.jpg",
                mimeType = "image/jpeg",
                isImage = true
            )
        )

        // Optimistic UI
        val optimisticMsg = viewModel.state.value.messages.find { it.type == "IMAGE" }
        assertNotNull(optimisticMsg)
        assertEquals("[Hình ảnh]", optimisticMsg!!.content)
        assertTrue(optimisticMsg.isPending)
        assertFalse(optimisticMsg.isFailed)

        advanceUntilIdle()

        // Đã gọi upload và sendMessage lên server
        assertEquals("IMAGE", lastSentType)
        assertEquals("https://pub-r2.skillexchange.dev/chats/chat_123/test.jpg", lastSentFileUrl)
        assertEquals("avatar.jpg", lastSentFileName)
        assertEquals(100L, lastSentFileSize)
    }

    @Test
    fun `SendAttachment rejects file exceeding size limit`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("chatId" to "chat_123"))
        val viewModel = ChatDetailViewModel(handle, fakeChatRepository, tokenManager, fakeExchangeRepository)
        roomFlow.emit(sampleRoom)
        advanceUntilIdle()

        var effectReceived: ChatDetailEffect? = null
        val job = launch {
            viewModel.effect.collect { effectReceived = it }
        }

        // 6MB > 5MB cho ảnh
        val oversizedImage = ByteArray(6 * 1024 * 1024)
        viewModel.onIntent(
            ChatDetailIntent.SendAttachment(
                fileBytes = oversizedImage,
                fileName = "huge.jpg",
                mimeType = "image/jpeg",
                isImage = true
            )
        )
        advanceUntilIdle()

        assertEquals(null, lastSentType)
        assertTrue(effectReceived is ChatDetailEffect.ShowSnackbar)
        assertTrue((effectReceived as ChatDetailEffect.ShowSnackbar).message.contains("5MB"))

        job.cancel()
    }
}
