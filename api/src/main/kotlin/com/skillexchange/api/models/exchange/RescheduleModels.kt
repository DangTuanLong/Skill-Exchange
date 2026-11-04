package com.skillexchange.api.models.exchange

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RescheduleStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}

@Serializable
data class CreateRescheduleRequest(
    @SerialName("new_scheduled_at") val newScheduledAt: String,
    val reason: String? = null
)

@Serializable
data class RescheduleProposalDto(
    val id: String,
    @SerialName("exchange_id") val exchangeId: String,
    @SerialName("proposer_id") val proposerId: String,
    @SerialName("new_scheduled_at") val newScheduledAt: String,
    val reason: String? = null,
    val status: String,
    @SerialName("proposal_number") val proposalNumber: Int,
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

