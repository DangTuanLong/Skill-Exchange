package com.skillexchange.app.presentation.notification

import com.skillexchange.app.domain.model.notification.AppNotification

data class NotificationGroup(
    val title: String,
    val items: List<AppNotification>
)

data class NotificationState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val groups: List<NotificationGroup> = emptyList(),
    val unreadCount: Long = 0,
    val error: String? = null
)

sealed class NotificationIntent {
    data object LoadNotifications : NotificationIntent()
    data object Refresh : NotificationIntent()
    data object MarkAllAsRead : NotificationIntent()
    data class NotificationClicked(val notification: AppNotification) : NotificationIntent()
}

sealed class NotificationEffect {
    data class NavigateToEntity(val type: String, val entityId: String) : NotificationEffect()
    data class ShowSnackbar(val message: String) : NotificationEffect()
}
