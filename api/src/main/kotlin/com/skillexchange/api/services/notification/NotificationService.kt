package com.skillexchange.api.services.notification

import com.skillexchange.api.models.db.NotificationsTable
import com.skillexchange.api.models.notification.NotificationDto
import com.skillexchange.api.models.notification.NotificationListResponseDto
import com.skillexchange.api.services.device.DeviceService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

open class NotificationService(
    private val fcmService: FcmService,
    private val deviceService: DeviceService
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    open fun getNotifications(userId: String, page: Int = 1, limit: Int = 30): NotificationListResponseDto = transaction {
        val safePage = maxOf(1, page)
        val safeLimit = minOf(100, maxOf(1, limit))
        val offset = ((safePage - 1) * safeLimit).toLong()

        val query = NotificationsTable
            .selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)

        val total = query.count()
        val unreadCount = NotificationsTable
            .selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) }
            .count()

        val list = query
            .limit(safeLimit, offset = offset)
            .map { it.toDto() }

        NotificationListResponseDto(
            notifications = list,
            unreadCount = unreadCount,
            total = total
        )
    }

    open fun markAsRead(userId: String, notificationId: String): Boolean = transaction {
        val nUuid = try {
            UUID.fromString(notificationId)
        } catch (e: Exception) {
            return@transaction false
        }

        val exists = NotificationsTable
            .selectAll()
            .where { (NotificationsTable.id eq nUuid) and (NotificationsTable.userId eq userId) }
            .count() > 0L

        if (!exists) return@transaction false

        NotificationsTable.update({ (NotificationsTable.id eq nUuid) and (NotificationsTable.userId eq userId) }) {
            it[isRead] = true
        } > 0
    }

    open fun markAllAsRead(userId: String): Int = transaction {
        NotificationsTable.update({ (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) }) {
            it[isRead] = true
        }
    }

    /**
     * Tạo thông báo và gửi FCM Push Notification.
     * Hỗ trợ chống trùng lặp (Idempotent): Nếu bắt lỗi duplicate key (do partial unique index), bỏ qua an toàn.
     */
    open fun createNotification(
        userId: String,
        type: String,
        title: String,
        body: String,
        entityId: String? = null,
        dataPayload: String = "{}"
    ): NotificationDto? {
        val createdDto = try {
            transaction {
                val newId = UUID.randomUUID()
                val now = LocalDateTime.now()
                NotificationsTable.insert {
                    it[id] = newId
                    it[NotificationsTable.userId] = userId
                    it[NotificationsTable.type] = type
                    it[NotificationsTable.title] = title
                    it[NotificationsTable.body] = body
                    it[NotificationsTable.entityId] = entityId
                    it[data] = dataPayload
                    it[isRead] = false
                    it[createdAt] = now
                }
                NotificationDto(
                    id = newId.toString(),
                    userId = userId,
                    type = type,
                    title = title,
                    body = body,
                    entityId = entityId,
                    data = dataPayload,
                    isRead = false,
                    createdAt = now.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        } catch (e: Exception) {
            // Xử lý duplicate key / conflict constraint
            if (e.message?.contains("unique", ignoreCase = true) == true ||
                e.message?.contains("idx_notifications_unique_reminders", ignoreCase = true) == true) {
                logger.info("Thông báo trùng lặp (đã tồn tại) cho user $userId, type $type, entityId $entityId -> bỏ qua")
                return null
            }
            logger.error("Lỗi khi ghi thông báo vào database: ${e.message}")
            return null
        }

        // Gửi push FCM song song không làm gián đoạn
        try {
            val tokens = deviceService.getTokensForUser(userId)
            if (tokens.isNotEmpty()) {
                val fcmData = mapOf(
                    "type" to type,
                    "entityId" to (entityId ?: "")
                )
                tokens.forEach { token ->
                    fcmService.sendPushAsync(
                        targetToken = token,
                        title = title,
                        body = body,
                        data = fcmData
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Lỗi khi gửi FCM push cho thông báo ${createdDto.id}: ${e.message}")
        }

        return createdDto
    }

    private fun ResultRow.toDto(): NotificationDto {
        return NotificationDto(
            id = this[NotificationsTable.id].toString(),
            userId = this[NotificationsTable.userId],
            type = this[NotificationsTable.type],
            title = this[NotificationsTable.title],
            body = this[NotificationsTable.body],
            entityId = this[NotificationsTable.entityId],
            data = this[NotificationsTable.data],
            isRead = this[NotificationsTable.isRead],
            createdAt = this[NotificationsTable.createdAt].format(DateTimeFormatter.ISO_DATE_TIME)
        )
    }
}
