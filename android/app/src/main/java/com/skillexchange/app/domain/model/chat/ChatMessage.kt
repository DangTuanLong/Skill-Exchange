package com.skillexchange.app.domain.model.chat

data class ChatMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val type: String = "TEXT",
    val createdAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null,
    val participants: List<String> = emptyList(),
    val isPending: Boolean = false,
    val isFailed: Boolean = false
) {
    fun isFromMe(currentUserId: String): Boolean = senderId == currentUserId
    val isRead: Boolean get() = readAt != null
}
