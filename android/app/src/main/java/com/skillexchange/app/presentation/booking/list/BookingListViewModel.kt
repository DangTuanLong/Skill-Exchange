package com.skillexchange.app.presentation.booking.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.repository.IExchangeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookingListViewModel(
    private val exchangeRepository: IExchangeRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        BookingListState(currentUserId = tokenManager.getUserId() ?: "")
    )
    val state = _state.asStateFlow()

    private val _effect = Channel<BookingListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadData()
    }

    fun onIntent(intent: BookingListIntent) {
        when (intent) {
            is BookingListIntent.LoadData -> loadData(isRefresh = false)
            is BookingListIntent.Refresh -> loadData(isRefresh = true)
            is BookingListIntent.SelectTab -> _state.update { it.copy(selectedTab = intent.tab) }
        }
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                    currentUserId = tokenManager.getUserId() ?: it.currentUserId
                )
            }

            val incomingDeferred = async { exchangeRepository.getIncomingRequests() }
            val outgoingDeferred = async { exchangeRepository.getOutgoingRequests() }

            val incomingRes = incomingDeferred.await()
            val outgoingRes = outgoingDeferred.await()

            if (incomingRes.isFailure && outgoingRes.isFailure) {
                val errorMsg = incomingRes.exceptionOrNull()?.message
                    ?: outgoingRes.exceptionOrNull()?.message
                    ?: "Không thể tải danh sách lịch hẹn"
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = errorMsg) }
                _effect.send(BookingListEffect.ShowSnackbar(errorMsg))
                return@launch
            }

            val allIncoming = incomingRes.getOrDefault(emptyList())
            val allOutgoing = outgoingRes.getOrDefault(emptyList())

            // Phân loại:
            // Tab "Nhận": Yêu cầu gửi đến người dùng, trạng thái PENDING hoặc ACCEPTED
            val activeIncoming = allIncoming.filter {
                it.status == ExchangeStatus.PENDING || it.status == ExchangeStatus.ACCEPTED
            }

            // Tab "Đã gửi": Yêu cầu người dùng gửi đi, trạng thái PENDING hoặc ACCEPTED
            val activeOutgoing = allOutgoing.filter {
                it.status == ExchangeStatus.PENDING || it.status == ExchangeStatus.ACCEPTED
            }

            // Tab "Lịch sử": Yêu cầu đã hoàn thành, bị từ chối hoặc đã hủy từ cả 2 chiều
            val terminalIncoming = allIncoming.filter {
                it.status == ExchangeStatus.COMPLETED ||
                it.status == ExchangeStatus.REJECTED ||
                it.status == ExchangeStatus.CANCELLED
            }
            val terminalOutgoing = allOutgoing.filter {
                it.status == ExchangeStatus.COMPLETED ||
                it.status == ExchangeStatus.REJECTED ||
                it.status == ExchangeStatus.CANCELLED
            }
            val history = (terminalIncoming + terminalOutgoing)
                .distinctBy { it.id }
                .sortedByDescending { it.scheduledAt }

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    incomingList = activeIncoming,
                    outgoingList = activeOutgoing,
                    historyList = history,
                    error = null
                )
            }
        }
    }
}
