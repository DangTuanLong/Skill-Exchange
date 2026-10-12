package com.skillexchange.app.domain.model.notification

enum class NotificationType {
    NEW_MESSAGE,
    NEW_REQUEST,
    REQUEST_ACCEPTED,
    REQUEST_REJECTED,
    APPOINTMENT_REMINDER,
    RATING_REMINDER,
    OTHER;

    companion object {
        fun fromString(type: String): NotificationType = when (type.trim().uppercase()) {
            "NEW_MESSAGE" -> NEW_MESSAGE
            "NEW_REQUEST" -> NEW_REQUEST
            "REQUEST_ACCEPTED" -> REQUEST_ACCEPTED
            "REQUEST_REJECTED" -> REQUEST_REJECTED
            "APPOINTMENT_REMINDER" -> APPOINTMENT_REMINDER
            "RATING_REMINDER" -> RATING_REMINDER
            else -> OTHER
        }
    }
}

data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val rawType: String,
    val title: String,
    val body: String,
    val entityId: String?,
    val data: String,
    val isRead: Boolean,
    val createdAt: String
)

data class NotificationListResult(
    val notifications: List<AppNotification>,
    val unreadCount: Long,
    val total: Long
)
