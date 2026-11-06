package com.skillexchange.app.presentation.booking.detail

import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal
import java.time.Duration
import java.time.Instant

data class BookingDetailState(
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val isRescheduleLoading: Boolean = false,
    val exchange: ExchangeRequest? = null,
    val rescheduleProposal: RescheduleProposal? = null,
    val currentUserId: String = "",
    val showCancelDialog: Boolean = false,
    val showRescheduleDialog: Boolean = false,
    val showRescheduleSuccessBanner: Boolean = false,
    val cancelReason: String = "",
    val hasRated: Boolean = false,
    val error: String? = null
) {
    val isSender: Boolean
        get() = exchange?.senderId == currentUserId

    val isReceiver: Boolean
        get() = exchange?.receiverId == currentUserId

    val status: ExchangeStatus?
        get() = exchange?.status

    val hasUserConfirmed: Boolean
        get() = exchange?.hasUserCompleted(currentUserId) == true

    val isWaitingForOtherToConfirm: Boolean
        get() = exchange?.isWaitingForOtherToConfirm(currentUserId) == true

    val otherPartyName: String
        get() = exchange?.otherUserName(currentUserId) ?: "Đối tác"

    /**
     * Kiểm tra đề xuất đổi lịch có đang PENDING và còn hiệu lực hay không.
     * Tính lười: nếu quá 24h hoặc giờ mới đã qua thì coi như hết hạn (DEC-025).
     */
    val isReschedulePending: Boolean
        get() {
            val prop = rescheduleProposal ?: return false
            if (!prop.isPending()) return false
            return try {
                val newTime = Instant.parse(prop.newScheduledAt)
                val createdTime = Instant.parse(prop.createdAt)
                val now = Instant.now()
                newTime.isAfter(now) && createdTime.plus(Duration.ofHours(24)).isAfter(now)
            } catch (_: Exception) {
                true
            }
        }

    val isRescheduleProposer: Boolean
        get() = rescheduleProposal?.isProposer(currentUserId) == true

    val canProposeReschedule: Boolean
        get() = status == ExchangeStatus.ACCEPTED &&
                !isReschedulePending &&
                (rescheduleProposal == null || rescheduleProposal.proposalNumber < 3)
}

sealed class BookingDetailIntent {
    data class LoadDetail(val exchangeId: String) : BookingDetailIntent()
    object AcceptRequest : BookingDetailIntent()
    object RejectRequest : BookingDetailIntent()
    object OpenCancelDialog : BookingDetailIntent()
    object DismissCancelDialog : BookingDetailIntent()
    data class UpdateCancelReason(val reason: String) : BookingDetailIntent()
    object ConfirmCancel : BookingDetailIntent()
    object CancelPendingRequest : BookingDetailIntent()
    object ConfirmCompletion : BookingDetailIntent()

    // Reschedule Intents (DEC-025, TASK-071)
    object OpenRescheduleDialog : BookingDetailIntent()
    object DismissRescheduleDialog : BookingDetailIntent()
    data class SubmitReschedule(val newScheduledAt: String, val reason: String? = null) : BookingDetailIntent()
    object AcceptRescheduleProposal : BookingDetailIntent()
    object RejectRescheduleProposal : BookingDetailIntent()
    object CancelRescheduleProposal : BookingDetailIntent()
    object DismissRescheduleSuccessBanner : BookingDetailIntent()
}

sealed class BookingDetailEffect {
    data class ShowSnackbar(val message: String) : BookingDetailEffect()
    object NavigateBack : BookingDetailEffect()
}
