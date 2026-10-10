package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getChatRoomsFlow(): Flow<List<ChatRoom>>
    fun getChatRoom(chatId: String): Flow<ChatRoom?>
    fun getMessagesFlow(chatId: String, limit: Long = 30L): Flow<List<ChatMessage>>
    fun generateNewMessageId(chatId: String): String
    suspend fun sendMessage(
        chatId: String,
        content: String,
        participants: List<String>,
        messageId: String = "",
        type: String = "TEXT",
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null
    ): Result<String>
    suspend fun uploadAttachment(
        chatId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<com.skillexchange.app.data.remote.chat.ChatAttachmentDataDto>
    suspend fun setTypingStatus(chatId: String, isTyping: Boolean): Result<Unit>
    fun observeOtherUserTyping(chatId: String, otherUserId: String): Flow<Boolean>
    suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit>
    suspend fun markMessageAsRead(chatId: String, messageId: String): Result<Unit> =
        markMessagesAsRead(chatId, listOf(messageId))
}
