package com.skillexchange.app.presentation.booking.detail

import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus

data class BookingDetailState(
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val exchange: ExchangeRequest? = null,
    val currentUserId: String = "",
    val showCancelDialog: Boolean = false,
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
}

sealed class BookingDetailEffect {
    data class ShowSnackbar(val message: String) : BookingDetailEffect()
    object NavigateBack : BookingDetailEffect()
}
