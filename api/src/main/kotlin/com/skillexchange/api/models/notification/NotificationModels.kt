package com.skillexchange.api.models.notification

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val entityId: String? = null,
    val data: String = "{}",
    val isRead: Boolean,
    val createdAt: String
)

@Serializable
data class NotificationListResponseDto(
    val notifications: List<NotificationDto>,
    val unreadCount: Long,
    val total: Long
)

@Serializable
data class MarkReadResponseDto(
    val success: Boolean,
    val message: String
)

@Serializable
data class ProcessRemindersResponseDto(
    val success: Boolean,
    val appointmentRemindersSent: Int,
    val ratingRemindersSent: Int,
    val message: String
)
