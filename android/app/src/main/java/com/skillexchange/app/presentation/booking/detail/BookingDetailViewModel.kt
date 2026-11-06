package com.skillexchange.app.presentation.booking.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IRatingRepository
import com.skillexchange.app.domain.repository.IRescheduleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookingDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val exchangeRepository: IExchangeRepository,
    private val tokenManager: TokenManager,
    private val ratingRepository: IRatingRepository,
    private val rescheduleRepository: IRescheduleRepository
) : ViewModel() {

    val exchangeId: String = savedStateHandle["exchangeId"] ?: ""

    private val _state = MutableStateFlow(BookingDetailState(currentUserId = tokenManager.getUserId() ?: ""))
    val state = _state.asStateFlow()

    private val _effect = Channel<BookingDetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        if (exchangeId.isNotBlank()) {
            loadDetail(exchangeId)
        }
    }

    fun onIntent(intent: BookingDetailIntent) {
        when (intent) {
            is BookingDetailIntent.LoadDetail -> loadDetail(intent.exchangeId)
            is BookingDetailIntent.AcceptRequest -> acceptRequest()
            is BookingDetailIntent.RejectRequest -> rejectRequest()
            is BookingDetailIntent.OpenCancelDialog -> _state.update { it.copy(showCancelDialog = true, cancelReason = "") }
            is BookingDetailIntent.DismissCancelDialog -> _state.update { it.copy(showCancelDialog = false) }
            is BookingDetailIntent.UpdateCancelReason -> _state.update { it.copy(cancelReason = intent.reason) }
            is BookingDetailIntent.ConfirmCancel -> cancelAcceptedRequest()
            is BookingDetailIntent.CancelPendingRequest -> cancelPendingRequest()
            is BookingDetailIntent.ConfirmCompletion -> confirmCompletion()

            // Reschedule Intents
            is BookingDetailIntent.OpenRescheduleDialog -> _state.update { it.copy(showRescheduleDialog = true) }
            is BookingDetailIntent.DismissRescheduleDialog -> _state.update { it.copy(showRescheduleDialog = false) }
            is BookingDetailIntent.DismissRescheduleSuccessBanner -> _state.update { it.copy(showRescheduleSuccessBanner = false) }
            is BookingDetailIntent.SubmitReschedule -> submitReschedule(intent.newScheduledAt, intent.reason)
            is BookingDetailIntent.AcceptRescheduleProposal -> acceptRescheduleProposal()
            is BookingDetailIntent.RejectRescheduleProposal -> rejectRescheduleProposal()
            is BookingDetailIntent.CancelRescheduleProposal -> cancelRescheduleProposal()
        }
    }

    fun loadDetail(targetExchangeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            exchangeRepository.getExchangeRequest(targetExchangeId)
                .onSuccess { exchange ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            exchange = exchange,
                            currentUserId = tokenManager.getUserId() ?: it.currentUserId
                        )
                    }
                    if (exchange.status == com.skillexchange.app.domain.model.exchange.ExchangeStatus.COMPLETED) {
                        checkRatingStatus(targetExchangeId)
                    }
                    if (exchange.status == com.skillexchange.app.domain.model.exchange.ExchangeStatus.ACCEPTED) {
                        loadCurrentProposal(targetExchangeId)
                    }
                }
                .onFailure { e ->
                    val errorMsg = e.message ?: "Không thể tải chi tiết yêu cầu"
                    _state.update { it.copy(isLoading = false, error = errorMsg) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(errorMsg))
                }
        }
    }

    private fun loadCurrentProposal(targetExchangeId: String) {
        viewModelScope.launch {
            rescheduleRepository.getCurrentProposal(targetExchangeId)
                .onSuccess { proposal ->
                    _state.update { it.copy(rescheduleProposal = proposal) }
                }
        }
    }

    private fun checkRatingStatus(targetExchangeId: String) {
        viewModelScope.launch {
            ratingRepository.getMyExchangeRating(targetExchangeId).onSuccess { ratingStatus ->
                _state.update { it.copy(hasRated = ratingStatus.hasRated) }
            }
        }
    }

    private fun acceptRequest() {
        if (_state.value.isActionLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            exchangeRepository.acceptRequest(exchangeId)
                .onSuccess { updated ->
                    _state.update { it.copy(isActionLoading = false, exchange = updated) }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã chấp nhận yêu cầu trao đổi"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isActionLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Chấp nhận thất bại"))
                }
        }
    }

    private fun rejectRequest() {
        if (_state.value.isActionLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            exchangeRepository.rejectRequest(exchangeId)
                .onSuccess { updated ->
                    _state.update { it.copy(isActionLoading = false, exchange = updated) }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã từ chối yêu cầu trao đổi"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isActionLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Từ chối thất bại"))
                }
        }
    }

    private fun cancelPendingRequest() {
        if (_state.value.isActionLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            exchangeRepository.cancelRequest(exchangeId, null)
                .onSuccess { updated ->
                    _state.update { it.copy(isActionLoading = false, exchange = updated) }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã hủy yêu cầu trao đổi"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isActionLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Hủy yêu cầu thất bại"))
                }
        }
    }

    private fun cancelAcceptedRequest() {
        if (_state.value.isActionLoading) return
        val reason = _state.value.cancelReason.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            exchangeRepository.cancelRequest(exchangeId, reason)
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            isActionLoading = false,
                            exchange = updated,
                            showCancelDialog = false
                        )
                    }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã hủy lịch hẹn"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isActionLoading = false, showCancelDialog = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Hủy lịch hẹn thất bại"))
                }
        }
    }

    private fun confirmCompletion() {
        if (_state.value.isActionLoading) return
        if (_state.value.isReschedulePending) {
            viewModelScope.launch {
                _effect.send(BookingDetailEffect.ShowSnackbar("Đang có đề xuất đổi lịch chờ xử lý, không thể hoàn tất"))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            exchangeRepository.completeRequest(exchangeId)
                .onSuccess { updated ->
                    _state.update { it.copy(isActionLoading = false, exchange = updated) }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã xác nhận hoàn thành buổi trao đổi"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isActionLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Xác nhận thất bại"))
                }
        }
    }

    // ── Reschedule Operations (TASK-071) ───────────────────────────────────

    private fun submitReschedule(newScheduledAt: String, reason: String?) {
        if (_state.value.isActionLoading || _state.value.isRescheduleLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isRescheduleLoading = true) }
            rescheduleRepository.createProposal(exchangeId, newScheduledAt, reason)
                .onSuccess { proposal ->
                    _state.update {
                        it.copy(
                            isRescheduleLoading = false,
                            showRescheduleDialog = false,
                            rescheduleProposal = proposal
                        )
                    }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã gửi đề xuất đổi lịch hẹn"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isRescheduleLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Gửi đề xuất đổi lịch thất bại"))
                }
        }
    }

    private fun acceptRescheduleProposal() {
        if (_state.value.isActionLoading || _state.value.isRescheduleLoading) return
        val proposalId = _state.value.rescheduleProposal?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRescheduleLoading = true) }
            rescheduleRepository.acceptProposal(proposalId)
                .onSuccess { updatedProposal ->
                    _state.update {
                        it.copy(
                            isRescheduleLoading = false,
                            rescheduleProposal = updatedProposal,
                            showRescheduleSuccessBanner = true,
                            exchange = it.exchange?.copy(scheduledAt = updatedProposal.newScheduledAt)
                        )
                    }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã đồng ý đổi lịch hẹn sang thời gian mới"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isRescheduleLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Chấp nhận đổi lịch thất bại"))
                }
        }
    }

    private fun rejectRescheduleProposal() {
        if (_state.value.isActionLoading || _state.value.isRescheduleLoading) return
        val proposalId = _state.value.rescheduleProposal?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRescheduleLoading = true) }
            rescheduleRepository.rejectProposal(proposalId)
                .onSuccess { updatedProposal ->
                    _state.update {
                        it.copy(
                            isRescheduleLoading = false,
                            rescheduleProposal = updatedProposal
                        )
                    }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã từ chối đề xuất đổi lịch"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isRescheduleLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Từ chối đề xuất thất bại"))
                }
        }
    }

    private fun cancelRescheduleProposal() {
        if (_state.value.isActionLoading || _state.value.isRescheduleLoading) return
        val proposalId = _state.value.rescheduleProposal?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRescheduleLoading = true) }
            rescheduleRepository.cancelProposal(proposalId)
                .onSuccess { updatedProposal ->
                    _state.update {
                        it.copy(
                            isRescheduleLoading = false,
                            rescheduleProposal = updatedProposal
                        )
                    }
                    _effect.send(BookingDetailEffect.ShowSnackbar("Đã hủy đề xuất đổi lịch"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isRescheduleLoading = false) }
                    _effect.send(BookingDetailEffect.ShowSnackbar(e.message ?: "Hủy đề xuất thất bại"))
                }
        }
    }
}
