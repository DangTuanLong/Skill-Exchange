package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.notification.NotificationDto
import com.skillexchange.app.data.remote.notification.NotificationRemoteDataSource
import com.skillexchange.app.domain.model.notification.AppNotification
import com.skillexchange.app.domain.model.notification.NotificationListResult
import com.skillexchange.app.domain.model.notification.NotificationType
import com.skillexchange.app.domain.repository.INotificationRepository

class NotificationRepositoryImpl(
    private val remoteDataSource: NotificationRemoteDataSource
) : INotificationRepository {

    override suspend fun getNotifications(page: Int, limit: Int): Result<NotificationListResult> = runCatching {
        val dto = remoteDataSource.getNotifications(page, limit)
        NotificationListResult(
            notifications = dto.notifications.map { it.toDomain() },
            unreadCount = dto.unreadCount,
            total = dto.total
        )
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        remoteDataSource.markAsRead(notificationId)
        Unit
    }

    override suspend fun markAllAsRead(): Result<Unit> = runCatching {
        remoteDataSource.markAllAsRead()
        Unit
    }

    private fun NotificationDto.toDomain() = AppNotification(
        id = id,
        userId = userId,
        type = NotificationType.fromString(type),
        rawType = type,
        title = title,
        body = body,
        entityId = entityId,
        data = data,
        isRead = isRead,
        createdAt = createdAt
    )
}
