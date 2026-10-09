package com.skillexchange.app.domain.model.chat

data class ChatRoom(
    val id: String,
    val exchangeId: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String,
    val receiverName: String,
    val senderAvatarUrl: String?,
    val receiverAvatarUrl: String?,
    val skillOfferedName: String,
    val skillWantedName: String,
    val status: String,
    val lastMessage: String?,
    val lastMessageAt: Long?,
    val lastSenderId: String?,
    val unreadCount: Int = 0,
    val participants: List<String> = emptyList(),
    val updatedAt: Long? = null
) {
    fun getOtherPartyName(currentUserId: String): String =
        if (currentUserId == senderId) receiverName else senderName

    fun getOtherPartyAvatar(currentUserId: String): String? =
        if (currentUserId == senderId) receiverAvatarUrl else senderAvatarUrl

    val isReadOnly: Boolean
        get() = status != "ACCEPTED"
}
