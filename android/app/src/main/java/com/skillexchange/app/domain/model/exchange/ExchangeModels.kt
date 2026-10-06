package com.skillexchange.app.domain.model.exchange

import com.skillexchange.app.data.remote.exchange.ExchangeRequestDto

enum class ExchangeStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    CANCELLED
}

enum class MeetingMode {
    ONLINE,
    IN_PERSON,
    UNDECIDED;

    fun displayName(): String = when (this) {
        ONLINE -> "Trực tuyến (Online)"
        IN_PERSON -> "Gặp trực tiếp"
        UNDECIDED -> "Chưa quyết định"
    }

    companion object {
        fun fromString(value: String): MeetingMode = when (value.uppercase()) {
            "ONLINE" -> ONLINE
            "IN_PERSON" -> IN_PERSON
            else -> UNDECIDED
        }
    }
}

data class ExchangeRequest(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val receiverName: String? = null,
    val receiverAvatarUrl: String? = null,
    val skillOfferedId: Int,
    val skillOfferedName: String,
    val skillWantedId: Int,
    val skillWantedName: String,
    val status: ExchangeStatus,
    val durationMinutes: Int,
    val meetingMode: MeetingMode,
    val message: String? = null,
    val cancellationReason: String? = null,
    val scheduledAt: String,
    val senderCompletedAt: String? = null,
    val receiverCompletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    fun isSender(currentUserId: String): Boolean = senderId == currentUserId

    fun otherUserName(currentUserId: String): String =
        if (isSender(currentUserId)) (receiverName ?: "Đối tác") else (senderName ?: "Đối tác")

    fun otherUserAvatar(currentUserId: String): String? =
        if (isSender(currentUserId)) receiverAvatarUrl else senderAvatarUrl

    fun teachSkillName(currentUserId: String): String =
        if (isSender(currentUserId)) skillOfferedName else skillWantedName

    fun learnSkillName(currentUserId: String): String =
        if (isSender(currentUserId)) skillWantedName else skillOfferedName

    fun hasUserCompleted(currentUserId: String): Boolean =
        if (isSender(currentUserId)) senderCompletedAt != null else receiverCompletedAt != null

    fun isWaitingForOtherToConfirm(currentUserId: String): Boolean =
        status == ExchangeStatus.ACCEPTED && hasUserCompleted(currentUserId) && !isBothCompleted()

    fun isBothCompleted(): Boolean =
        senderCompletedAt != null && receiverCompletedAt != null
}

fun ExchangeRequestDto.toDomain(): ExchangeRequest {
    val domainStatus = runCatching { ExchangeStatus.valueOf(status.uppercase()) }
        .getOrDefault(ExchangeStatus.PENDING)
    val domainMode = MeetingMode.fromString(meetingMode)

    return ExchangeRequest(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        senderName = senderName,
        senderAvatarUrl = senderAvatarUrl,
        receiverName = receiverName,
        receiverAvatarUrl = receiverAvatarUrl,
        skillOfferedId = skillOfferedId,
        skillOfferedName = skillOfferedName,
        skillWantedId = skillWantedId,
        skillWantedName = skillWantedName,
        status = domainStatus,
        durationMinutes = durationMinutes,
        meetingMode = domainMode,
        message = message,
        cancellationReason = cancellationReason,
        scheduledAt = scheduledAt,
        senderCompletedAt = senderCompletedAt,
        receiverCompletedAt = receiverCompletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
