package com.skillexchange.app.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.model.notification.AppNotification
import com.skillexchange.app.domain.repository.INotificationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NotificationViewModel(
    private val notificationRepository: INotificationRepository,
    private val nowProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    private val _effect = Channel<NotificationEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    init {
        loadNotifications()
    }

    fun onIntent(intent: NotificationIntent) {
        when (intent) {
            NotificationIntent.LoadNotifications -> loadNotifications()
            NotificationIntent.Refresh -> refresh()
            NotificationIntent.MarkAllAsRead -> markAllAsRead()
            is NotificationIntent.NotificationClicked -> onNotificationClicked(intent.notification)
        }
    }

    private fun loadNotifications() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            notificationRepository.getNotifications(page = 1, limit = 50).fold(
                onSuccess = { result ->
                    val groups = groupNotifications(result.notifications)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            notifications = result.notifications,
                            groups = groups,
                            unreadCount = result.unreadCount,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Không thể tải danh sách thông báo"
                        )
                    }
                }
            )
        }
    }

    private fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            notificationRepository.getNotifications(page = 1, limit = 50).fold(
                onSuccess = { result ->
                    val groups = groupNotifications(result.notifications)
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            notifications = result.notifications,
                            groups = groups,
                            unreadCount = result.unreadCount,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isRefreshing = false) }
                    _effect.send(NotificationEffect.ShowSnackbar(error.message ?: "Làm mới thất bại"))
                }
            )
        }
    }

    private fun onNotificationClicked(notification: AppNotification) {
        // 1. Optimistic Update: Đánh dấu đã đọc trên UI ngay lập tức
        if (!notification.isRead) {
            val updatedList = _state.value.notifications.map {
                if (it.id == notification.id) it.copy(isRead = true) else it
            }
            val newUnreadCount = maxOf(0L, _state.value.unreadCount - 1)
            val updatedGroups = groupNotifications(updatedList)

            _state.update {
                it.copy(
                    notifications = updatedList,
                    groups = updatedGroups,
                    unreadCount = newUnreadCount
                )
            }

            // Gọi API lưu trạng thái đọc trong nền
            viewModelScope.launch {
                notificationRepository.markAsRead(notification.id)
            }
        }

        // 2. Điều hướng theo entityId
        val entityId = notification.entityId
        if (!entityId.isNullOrBlank()) {
            viewModelScope.launch {
                _effect.send(NotificationEffect.NavigateToEntity(notification.rawType, entityId))
            }
        } else {
            viewModelScope.launch {
                _effect.send(NotificationEffect.ShowSnackbar("Thông báo không có nội dung liên kết hoặc nội dung đã bị xóa"))
            }
        }
    }

    private fun markAllAsRead() {
        if (_state.value.unreadCount == 0L) return

        // Optimistic Update
        val updatedList = _state.value.notifications.map { it.copy(isRead = true) }
        val updatedGroups = groupNotifications(updatedList)
        _state.update {
            it.copy(
                notifications = updatedList,
                groups = updatedGroups,
                unreadCount = 0L
            )
        }

        viewModelScope.launch {
            notificationRepository.markAllAsRead().onFailure { error ->
                _effect.send(NotificationEffect.ShowSnackbar(error.message ?: "Không thể đánh dấu đã đọc tất cả"))
            }
        }
    }

    internal fun groupNotifications(list: List<AppNotification>): List<NotificationGroup> {
        if (list.isEmpty()) return emptyList()

        val today = nowProvider()
        val yesterday = today.minusDays(1)

        val dateToItems = LinkedHashMap<String, MutableList<AppNotification>>()

        for (item in list) {
            val date = parseDate(item.createdAt)
            val headerTitle = when {
                date == null -> "Khác"
                date == today -> "Hôm nay"
                date == yesterday -> "Hôm qua"
                else -> date.format(dateFormatter)
            }

            dateToItems.getOrPut(headerTitle) { mutableListOf() }.add(item)
        }

        return dateToItems.map { (title, items) ->
            NotificationGroup(title = title, items = items)
        }
    }

    private fun parseDate(dateStr: String): LocalDate? {
        return runCatching {
            if (dateStr.contains("T")) {
                if (dateStr.endsWith("Z") || dateStr.contains("+")) {
                    ZonedDateTime.parse(dateStr).toLocalDate()
                } else {
                    LocalDateTime.parse(dateStr).toLocalDate()
                }
            } else {
                LocalDate.parse(dateStr.substringBefore(" "))
            }
        }.getOrNull()
    }

    private fun String?.isNullExBlank(): Boolean = this == null || this.trim().isEmpty()
}
