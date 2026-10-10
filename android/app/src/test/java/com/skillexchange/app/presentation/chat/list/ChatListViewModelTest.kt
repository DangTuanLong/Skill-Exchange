package com.skillexchange.app.presentation.chat.list

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.IChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ChatListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_me") }

    private val sampleRooms = listOf(
        ChatRoom(
            id = "chat_1",
            exchangeId = "chat_1",
            senderId = "user_me",
            receiverId = "user_other_1",
            senderName = "Tôi",
            receiverName = "Nguyễn Văn A",
            senderAvatarUrl = null,
            receiverAvatarUrl = null,
            skillOfferedName = "Kotlin",
            skillWantedName = "Figma",
            status = "ACCEPTED",
            lastMessage = "Xin chào bạn!",
            lastMessageAt = 1000L,
            lastSenderId = "user_other_1",
            unreadCount = 1,
            participants = listOf("user_me", "user_other_1")
        ),
        ChatRoom(
            id = "chat_2",
            exchangeId = "chat_2",
            senderId = "user_other_2",
            receiverId = "user_me",
            senderName = "Trần Thị B",
            receiverName = "Tôi",
            senderAvatarUrl = null,
            receiverAvatarUrl = null,
            skillOfferedName = "Guitar",
            skillWantedName = "Tiếng Anh",
            status = "COMPLETED",
            lastMessage = "Cảm ơn bạn nhé",
            lastMessageAt = 2000L,
            lastSenderId = "user_me",
            unreadCount = 0,
            participants = listOf("user_other_2", "user_me")
        )
    )

    private val roomsFlow = MutableSharedFlow<List<ChatRoom>>(replay = 1)

    private val fakeChatRepository = object : IChatRepository {
        override fun getChatRoomsFlow(): Flow<List<ChatRoom>> = roomsFlow.asSharedFlow()
        override fun getChatRoom(chatId: String): Flow<ChatRoom?> = flowOf(null)
        override fun getMessagesFlow(chatId: String, limit: Long): Flow<List<ChatMessage>> = flowOf(emptyList())
        override fun generateNewMessageId(chatId: String): String = "dummy_msg_id"
        override suspend fun sendMessage(
            chatId: String,
            content: String,
            participants: List<String>,
            messageId: String,
            type: String,
            fileUrl: String?,
            fileName: String?,
            fileSize: Long?
        ): Result<String> = Result.success("dummy_msg_id")

        override suspend fun uploadAttachment(
            chatId: String,
            fileBytes: ByteArray,
            fileName: String,
            mimeType: String
        ): Result<com.skillexchange.app.data.remote.chat.ChatAttachmentDataDto> =
            Result.success(
                com.skillexchange.app.data.remote.chat.ChatAttachmentDataDto(
                    url = "https://example.com/file",
                    type = "IMAGE",
                    fileName = fileName,
                    fileSize = fileBytes.size.toLong()
                )
            )

        override suspend fun setTypingStatus(chatId: String, isTyping: Boolean): Result<Unit> = Result.success(Unit)
        override fun observeOtherUserTyping(chatId: String, otherUserId: String): Flow<Boolean> = flowOf(false)
        override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state collects rooms and updates filtered list`() = runTest(testDispatcher) {
        val viewModel = ChatListViewModel(fakeChatRepository, tokenManager)
        assertTrue(viewModel.state.value.isLoading)

        roomsFlow.emit(sampleRooms)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.rooms.size)
        assertEquals(2, state.filteredRooms.size)
    }

    @Test
    fun `search filter filters by other user name`() = runTest(testDispatcher) {
        val viewModel = ChatListViewModel(fakeChatRepository, tokenManager)
        roomsFlow.emit(sampleRooms)
        advanceUntilIdle()

        viewModel.onIntent(ChatListIntent.SearchQueryChanged("Nguyễn"))
        advanceUntilIdle()

        val filtered = viewModel.state.value.filteredRooms
        assertEquals(1, filtered.size)
        assertEquals("chat_1", filtered[0].id)
    }

    @Test
    fun `search filter filters by skill name`() = runTest(testDispatcher) {
        val viewModel = ChatListViewModel(fakeChatRepository, tokenManager)
        roomsFlow.emit(sampleRooms)
        advanceUntilIdle()

        viewModel.onIntent(ChatListIntent.SearchQueryChanged("Guitar"))
        advanceUntilIdle()

        val filtered = viewModel.state.value.filteredRooms
        assertEquals(1, filtered.size)
        assertEquals("chat_2", filtered[0].id)
    }

    @Test
    fun `OpenChat intent emits NavigateToChat effect`() = runTest(testDispatcher) {
        val viewModel = ChatListViewModel(fakeChatRepository, tokenManager)
        var emittedEffect: ChatListEffect? = null

        val job = launch {
            viewModel.effect.collect { emittedEffect = it }
        }

        viewModel.onIntent(ChatListIntent.OpenChat("chat_1"))
        advanceUntilIdle()

        assertNotNull(emittedEffect)
        assertTrue(emittedEffect is ChatListEffect.NavigateToChat)
        assertEquals("chat_1", (emittedEffect as ChatListEffect.NavigateToChat).chatId)

        job.cancel()
    }
}
