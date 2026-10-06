package com.skillexchange.api.models.exchange

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExchangeStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    CANCELLED
}

@Serializable
enum class MeetingMode {
    ONLINE,
    IN_PERSON,
    UNDECIDED
}

@Serializable
data class CreateExchangeRequest(
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("skill_offered_id") val skillOfferedId: Int,
    @SerialName("skill_wanted_id") val skillWantedId: Int,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int = 60,
    @SerialName("meeting_mode") val meetingMode: String = "UNDECIDED",
    val message: String? = null
)

@Serializable
data class CancelExchangeRequest(
    val reason: String? = null
)

@Serializable
data class ExchangeRequestDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_avatar_url") val senderAvatarUrl: String? = null,
    @SerialName("receiver_name") val receiverName: String? = null,
    @SerialName("receiver_avatar_url") val receiverAvatarUrl: String? = null,
    @SerialName("skill_offered_id") val skillOfferedId: Int,
    @SerialName("skill_offered_name") val skillOfferedName: String,
    @SerialName("skill_wanted_id") val skillWantedId: Int,
    @SerialName("skill_wanted_name") val skillWantedName: String,
    val status: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("meeting_mode") val meetingMode: String,
    val message: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("sender_completed_at") val senderCompletedAt: String? = null,
    @SerialName("receiver_completed_at") val receiverCompletedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
