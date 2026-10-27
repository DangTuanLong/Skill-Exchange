package com.skillexchange.api.services.notification

import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.NotificationsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.RatingsTable
import com.skillexchange.api.models.notification.ProcessRemindersResponseDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

open class ReminderSchedulerService(
    private val notificationService: NotificationService,
    private val timeProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val callService: com.skillexchange.api.services.call.CallService? = null
) {
    private val logger = LoggerFactory.getLogger(ReminderSchedulerService::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM")

    open fun processReminders(): ProcessRemindersResponseDto {
        val now = timeProvider()
        logger.info("Bắt đầu quét nhắc nhở tại thời điểm: $now")

        var appointmentRemindersCount = 0
        var ratingRemindersCount = 0

        try {
            // 1. Quét nhắc lịch hẹn trước 1 giờ (APPOINTMENT_REMINDER)
            appointmentRemindersCount = processAppointmentReminders(now)

            // 2. Quét nhắc đánh giá sau 24 giờ kể từ COMPLETED (RATING_REMINDER)
            ratingRemindersCount = processRatingReminders(now)

            // 3. Dọn dẹp các cuộc gọi hết hạn (DEC-026, TASK-073)
            callService?.cleanupExpiredCalls()
        } catch (e: Throwable) {
            logger.error("Lỗi khi quét reminders: ${e.message}", e)
        }

        logger.info("Hoàn thành quét reminders: $appointmentRemindersCount nhắc hẹn, $ratingRemindersCount nhắc đánh giá")

        return ProcessRemindersResponseDto(
            success = true,
            appointmentRemindersSent = appointmentRemindersCount,
            ratingRemindersSent = ratingRemindersCount,
            message = "Đã quét và xử lý $appointmentRemindersCount nhắc hẹn và $ratingRemindersCount nhắc đánh giá"
        )
    }

    private fun processAppointmentReminders(now: LocalDateTime): Int = transaction {
        var count = 0
        // Cửa sổ: [now + 10 phút, now + 70 phút]
        val minTime = now.plusMinutes(10)
        val maxTime = now.plusMinutes(70)

        // Quét các exchange ACCEPTED
        val candidates = ExchangeRequestsTable
            .selectAll()
            .where {
                (ExchangeRequestsTable.status eq "ACCEPTED") and
                (ExchangeRequestsTable.scheduledAt greaterEq minTime) and
                (ExchangeRequestsTable.scheduledAt lessEq maxTime)
            }
            .toList()

        for (row in candidates) {
            val exchangeId = row[ExchangeRequestsTable.id].toString()
            val senderId = row[ExchangeRequestsTable.senderId]
            val receiverId = row[ExchangeRequestsTable.receiverId]
            val scheduledAt = row[ExchangeRequestsTable.scheduledAt]
            val timeText = scheduledAt.format(timeFormatter)

            val names = getUserNames(senderId, receiverId)
            val senderName = names[senderId] ?: "Đối tác"
            val receiverName = names[receiverId] ?: "Đối tác"

            // Gửi nhắc cho Sender
            val notifSender = notificationService.createNotification(
                userId = senderId,
                type = "APPOINTMENT_REMINDER",
                title = "Nhắc lịch hẹn sắp tới",
                body = "Bạn có lịch hẹn với $receiverName lúc $timeText",
                entityId = exchangeId,
                dataPayload = """{"exchangeId":"$exchangeId","scheduledAt":"$scheduledAt"}"""
            )
            if (notifSender != null) count++

            // Gửi nhắc cho Receiver
            val notifReceiver = notificationService.createNotification(
                userId = receiverId,
                type = "APPOINTMENT_REMINDER",
                title = "Nhắc lịch hẹn sắp tới",
                body = "Bạn có lịch hẹn với $senderName lúc $timeText",
                entityId = exchangeId,
                dataPayload = """{"exchangeId":"$exchangeId","scheduledAt":"$scheduledAt"}"""
            )
            if (notifReceiver != null) count++
        }

        count
    }

    private fun processRatingReminders(now: LocalDateTime): Int = transaction {
        var count = 0
        val threshold = now.minusHours(24)

        // Quét các exchange COMPLETED
        val completedExchanges = ExchangeRequestsTable
            .selectAll()
            .where { ExchangeRequestsTable.status eq "COMPLETED" }
            .toList()

        for (row in completedExchanges) {
            val senderComp = row[ExchangeRequestsTable.senderCompletedAt]
            val receiverComp = row[ExchangeRequestsTable.receiverCompletedAt]
            val updatedAt = row[ExchangeRequestsTable.updatedAt]

            // Mốc COMPLETED = GREATEST(sender_completed_at, receiver_completed_at)
            val completedAt = when {
                senderComp != null && receiverComp != null -> maxOf(senderComp, receiverComp)
                senderComp != null -> senderComp
                receiverComp != null -> receiverComp
                else -> updatedAt
            }

            // Nếu chưa đủ 24h thì bỏ qua
            if (completedAt.isAfter(threshold)) {
                continue
            }

            val exchangeId = row[ExchangeRequestsTable.id].toString()
            val exchangeUuid = row[ExchangeRequestsTable.id]
            val senderId = row[ExchangeRequestsTable.senderId]
            val receiverId = row[ExchangeRequestsTable.receiverId]

            val names = getUserNames(senderId, receiverId)
            val senderName = names[senderId] ?: "Đối tác"
            val receiverName = names[receiverId] ?: "Đối tác"

            // Kiểm tra Sender đã rate chưa
            val senderRated = RatingsTable
                .selectAll()
                .where {
                    (RatingsTable.exchangeId eq exchangeUuid) and
                    (RatingsTable.reviewerId eq senderId)
                }
                .count() > 0L

            if (!senderRated) {
                val notif = notificationService.createNotification(
                    userId = senderId,
                    type = "RATING_REMINDER",
                    title = "Đánh giá buổi trao đổi",
                    body = "Hãy đánh giá phiên trao đổi với $receiverName",
                    entityId = exchangeId,
                    dataPayload = """{"exchangeId":"$exchangeId"}"""
                )
                if (notif != null) count++
            }

            // Kiểm tra Receiver đã rate chưa
            val receiverRated = RatingsTable
                .selectAll()
                .where {
                    (RatingsTable.exchangeId eq exchangeUuid) and
                    (RatingsTable.reviewerId eq receiverId)
                }
                .count() > 0L

            if (!receiverRated) {
                val notif = notificationService.createNotification(
                    userId = receiverId,
                    type = "RATING_REMINDER",
                    title = "Đánh giá buổi trao đổi",
                    body = "Hãy đánh giá phiên trao đổi với $senderName",
                    entityId = exchangeId,
                    dataPayload = """{"exchangeId":"$exchangeId"}"""
                )
                if (notif != null) count++
            }
        }

        count
    }

    private fun getUserNames(user1: String, user2: String): Map<String, String> {
        val ids = listOf(user1, user2).filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyMap()

        return ProfilesTable
            .selectAll()
            .where { ProfilesTable.userId inList ids }
            .associate { it[ProfilesTable.userId] to it[ProfilesTable.fullName] }
    }
}
