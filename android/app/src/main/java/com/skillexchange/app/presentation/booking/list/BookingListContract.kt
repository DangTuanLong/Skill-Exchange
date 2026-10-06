package com.skillexchange.app.presentation.booking.list

import com.skillexchange.app.domain.model.exchange.ExchangeRequest

enum class BookingTab(val title: String) {
    INCOMING("Nhận"),
    OUTGOING("Đã gửi"),
    HISTORY("Lịch sử")
}

data class BookingListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: BookingTab = BookingTab.INCOMING,
    val incomingList: List<ExchangeRequest> = emptyList(),
    val outgoingList: List<ExchangeRequest> = emptyList(),
    val historyList: List<ExchangeRequest> = emptyList(),
    val error: String? = null,
    val currentUserId: String = ""
) {
    val currentList: List<ExchangeRequest>
        get() = when (selectedTab) {
            BookingTab.INCOMING -> incomingList
            BookingTab.OUTGOING -> outgoingList
            BookingTab.HISTORY -> historyList
        }
}

sealed class BookingListIntent {
    object LoadData : BookingListIntent()
    object Refresh : BookingListIntent()
    data class SelectTab(val tab: BookingTab) : BookingListIntent()
}

sealed class BookingListEffect {
    data class NavigateToDetail(val exchangeId: String) : BookingListEffect()
    data class ShowSnackbar(val message: String) : BookingListEffect()
}
