package com.skillexchange.app.domain.model.reschedule

import com.skillexchange.app.data.remote.reschedule.RescheduleProposalDto

enum class RescheduleStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    fun displayName(): String = when (this) {
        PENDING -> "Chờ phản hồi"
        ACCEPTED -> "Đã chấp nhận"
        REJECTED -> "Đã từ chối"
        CANCELLED -> "Đã hủy"
        EXPIRED -> "Đã hết hạn"
    }

    companion object {
        fun fromString(value: String): RescheduleStatus = runCatching {
            valueOf(value.uppercase())
        }.getOrDefault(PENDING)
    }
}

data class RescheduleProposal(
    val id: String,
    val exchangeId: String,
    val proposerId: String,
    val newScheduledAt: String,
    val reason: String? = null,
    val status: RescheduleStatus,
    val proposalNumber: Int,
    val respondedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    fun isProposer(currentUserId: String): Boolean = proposerId == currentUserId

    fun isPending(): Boolean = status == RescheduleStatus.PENDING
}

fun RescheduleProposalDto.toDomain(): RescheduleProposal = RescheduleProposal(
    id = id,
    exchangeId = exchangeId,
    proposerId = proposerId,
    newScheduledAt = newScheduledAt,
    reason = reason,
    status = RescheduleStatus.fromString(status),
    proposalNumber = proposalNumber,
    respondedAt = respondedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

