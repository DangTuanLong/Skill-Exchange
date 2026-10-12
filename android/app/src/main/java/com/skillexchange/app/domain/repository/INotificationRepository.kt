package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.notification.NotificationListResult

interface INotificationRepository {
    suspend fun getNotifications(page: Int = 1, limit: Int = 30): Result<NotificationListResult>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun markAllAsRead(): Result<Unit>
}
