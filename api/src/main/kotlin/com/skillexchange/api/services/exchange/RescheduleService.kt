package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.RescheduleProposalsTable
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import com.skillexchange.api.models.exchange.RescheduleProposalDto
import com.skillexchange.api.models.exchange.RescheduleStatus
import com.skillexchange.api.services.notification.NotificationService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

open class RescheduleService(
    private val notificationService: NotificationService? = null,
    private val timeProvider: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val logger = LoggerFactory.getLogger(RescheduleService::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM")

    private data class ProposedNotificationData(
        val dto: RescheduleProposalDto,
        val partnerId: String,
        val callerName: String
    )

    private data class AcceptNotificationData(
        val dto: RescheduleProposalDto,
        val shouldNotify: Boolean,
        val proposerId: String,
        val callerName: String,
        val newScheduledAt: LocalDateTime,
        val exchangeId: String
    )

    private data class RejectNotificationData(
        val dto: RescheduleProposalDto,
        val shouldNotify: Boolean,
        val proposerId: String,
        val callerName: String,
        val exchangeId: String
    )

    open fun createProposal(
        callerUserId: String,
        exchangeId: String,
        req: CreateRescheduleRequest
    ): RescheduleProposalDto {
        val exchangeUuid = RescheduleLogicHelper.parseExchangeUuid(exchangeId)
        val cleanReason = RescheduleLogicHelper.validateReason(req.reason)
        val now = timeProvider()
        val newScheduledAt = RescheduleLogicHelper.validateNewScheduledAt(req.newScheduledAt, now)

        val notificationData = transaction {
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .forUpdate()
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            val exchangeStatus = exchangeRow[ExchangeRequestsTable.status]

            RescheduleLogicHelper.validateParticipant(callerUserId, senderId, receiverId)
            RescheduleLogicHelper.validateExchangeAccepted(exchangeStatus)

            // Đếm tổng số đề xuất từng tạo của exchange trên MỌI trạng thái (DEC-025 anti-spam)
            val totalCount = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.exchangeId eq exchangeUuid }
                .count()
            RescheduleLogicHelper.checkProposalLimit(totalCount)

            // Kiểm tra đề xuất PENDING hiện tại (có tính lazy expiration)
            val pendingProposals = RescheduleProposalsTable.selectAll()
                .where {
                    (RescheduleProposalsTable.exchangeId eq exchangeUuid) and
                    (RescheduleProposalsTable.status eq RescheduleStatus.PENDING.name)
                }
                .toList()

            for (p in pendingProposals) {
                val pId = p[RescheduleProposalsTable.id]
                val pCreatedAt = p[RescheduleProposalsTable.createdAt]
                val pNewScheduledAt = p[RescheduleProposalsTable.newScheduledAt]
                val pStatus = p[RescheduleProposalsTable.status]

                if (RescheduleLogicHelper.isProposalExpired(pStatus, pCreatedAt, pNewScheduledAt, now)) {
                    // Tự động chuyển thành EXPIRED (lazy expiration)
                    RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq pId }) {
                        it[status] = RescheduleStatus.EXPIRED.name
                        it[updatedAt] = now
                    }
                } else {
                    // Vẫn còn PENDING hợp lệ -> xung đột 409
                    throw RescheduleConflictException(
                        "Đang có đề xuất đổi lịch chờ phản hồi cho phiên trao đổi này.",
                        ErrorCodes.PENDING_PROPOSAL_EXISTS
                    )
                }
            }

            val newProposalId = UUID.randomUUID()
            val nextNumber = (totalCount + 1).toInt()

            try {
                RescheduleProposalsTable.insert {
                    it[RescheduleProposalsTable.id] = newProposalId
                    it[RescheduleProposalsTable.exchangeId] = exchangeUuid
                    it[RescheduleProposalsTable.proposerId] = callerUserId
                    it[RescheduleProposalsTable.newScheduledAt] = newScheduledAt
                    it[RescheduleProposalsTable.reason] = cleanReason
                    it[RescheduleProposalsTable.status] = RescheduleStatus.PENDING.name
                    it[RescheduleProposalsTable.proposalNumber] = nextNumber
                    it[RescheduleProposalsTable.respondedAt] = null
                    it[RescheduleProposalsTable.createdAt] = now
                    it[RescheduleProposalsTable.updatedAt] = now
                }
            } catch (e: Exception) {
                // DEC-025 / TASK-072: Bắt vi phạm partial unique index idx_unique_pending_reschedule (SQLState 23505)
                if (isUniquePendingRescheduleViolation(e)) {
                    throw RescheduleConflictException(
                        "Đang có đề xuất đổi lịch chờ phản hồi cho phiên trao đổi này.",
                        ErrorCodes.PENDING_PROPOSAL_EXISTS
                    )
                }
                throw e
            }

            val createdRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq newProposalId }
                .first()

            val partnerId = if (callerUserId == senderId) receiverId else senderId
            val callerName = getUserName(callerUserId)
            ProposedNotificationData(
                dto = createdRow.toDto(),
                partnerId = partnerId,
                callerName = callerName
            )
        }

        try {
            notificationService?.createNotification(
                userId = notificationData.partnerId,
                type = "RESCHEDULE_PROPOSED",
                title = "Đề xuất đổi lịch hẹn",
                body = RescheduleLogicHelper.formatProposedNotificationBody(notificationData.callerName, newScheduledAt),
                entityId = exchangeId,
                dataPayload = RescheduleLogicHelper.formatRescheduleDataPayload(exchangeId, notificationData.dto.id)
            )
        } catch (e: Throwable) {
            logger.error("Lỗi khi gửi thông báo RESCHEDULE_PROPOSED cho exchange $exchangeId: ${e.message}", e)
        }

        return notificationData.dto
    }

    open fun getCurrentProposal(callerUserId: String, exchangeId: String): RescheduleProposalDto? {
        val exchangeUuid = RescheduleLogicHelper.parseExchangeUuid(exchangeId)
        val now = timeProvider()

        return transaction {
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            RescheduleLogicHelper.validateParticipant(callerUserId, senderId, receiverId)

            val latestRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.exchangeId eq exchangeUuid }
                .orderBy(RescheduleProposalsTable.createdAt to SortOrder.DESC)
                .firstOrNull() ?: return@transaction null

            val status = latestRow[RescheduleProposalsTable.status]
            val createdAt = latestRow[RescheduleProposalsTable.createdAt]
            val newScheduledAt = latestRow[RescheduleProposalsTable.newScheduledAt]

            if (RescheduleLogicHelper.isProposalExpired(status, createdAt, newScheduledAt, now)) {
                RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq latestRow[RescheduleProposalsTable.id] }) {
                    it[RescheduleProposalsTable.status] = RescheduleStatus.EXPIRED.name
                    it[updatedAt] = now
                }
                latestRow.toDto().copy(status = RescheduleStatus.EXPIRED.name, updatedAt = now.format(DateTimeFormatter.ISO_DATE_TIME))
            } else {
                latestRow.toDto()
            }
        }
    }

    open fun acceptProposal(callerUserId: String, proposalId: String): RescheduleProposalDto {
        val proposalUuid = RescheduleLogicHelper.parseProposalUuid(proposalId)
        val now = timeProvider()

        val notificationData = transaction {
            val proposalRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .forUpdate()
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy đề xuất đổi lịch")

            val exchangeUuid = proposalRow[RescheduleProposalsTable.exchangeId]
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .forUpdate()
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            val proposerId = proposalRow[RescheduleProposalsTable.proposerId]
            val currentStatus = proposalRow[RescheduleProposalsTable.status]

            RescheduleLogicHelper.validateParticipant(callerUserId, senderId, receiverId)

            val canProceed = RescheduleLogicHelper.validateAcceptEligibility(callerUserId, proposerId, currentStatus)
            if (!canProceed) {
                return@transaction AcceptNotificationData(
                    dto = proposalRow.toDto(),
                    shouldNotify = false,
                    proposerId = proposerId,
                    callerName = "",
                    newScheduledAt = now,
                    exchangeId = exchangeUuid.toString()
                )
            }

            // Kiểm tra lazy expiration
            val createdAt = proposalRow[RescheduleProposalsTable.createdAt]
            val newScheduledAt = proposalRow[RescheduleProposalsTable.newScheduledAt]
            if (RescheduleLogicHelper.isProposalExpired(currentStatus, createdAt, newScheduledAt, now)) {
                RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq proposalUuid }) {
                    it[status] = RescheduleStatus.EXPIRED.name
                    it[updatedAt] = now
                }
                throw RescheduleConflictException("Đề xuất đổi lịch đã hết hạn.", ErrorCodes.PROPOSAL_NOT_PENDING)
            }

            // Cập nhật proposal -> ACCEPTED
            RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq proposalUuid }) {
                it[status] = RescheduleStatus.ACCEPTED.name
                it[respondedAt] = now
                it[updatedAt] = now
            }

            // Cập nhật exchange_requests.scheduled_at = newScheduledAt (KHÔNG xóa thông báo nhắc hẹn cũ)
            ExchangeRequestsTable.update({ ExchangeRequestsTable.id eq exchangeUuid }) {
                it[scheduledAt] = newScheduledAt
                it[updatedAt] = now
            }

            val updatedRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .first()

            val callerName = getUserName(callerUserId)
            AcceptNotificationData(
                dto = updatedRow.toDto(),
                shouldNotify = true,
                proposerId = proposerId,
                callerName = callerName,
                newScheduledAt = newScheduledAt,
                exchangeId = exchangeUuid.toString()
            )
        }

        if (notificationData.shouldNotify) {
            try {
                notificationService?.createNotification(
                    userId = notificationData.proposerId,
                    type = "RESCHEDULE_ACCEPTED",
                    title = "Đã đồng ý đổi lịch hẹn",
                    body = RescheduleLogicHelper.formatAcceptedNotificationBody(notificationData.callerName, notificationData.newScheduledAt),
                    entityId = notificationData.exchangeId,
                    dataPayload = RescheduleLogicHelper.formatRescheduleDataPayload(notificationData.exchangeId, notificationData.dto.id)
                )
            } catch (e: Throwable) {
                logger.error("Lỗi khi gửi thông báo RESCHEDULE_ACCEPTED: ${e.message}", e)
            }
        }

        return notificationData.dto
    }

    open fun rejectProposal(callerUserId: String, proposalId: String): RescheduleProposalDto {
        val proposalUuid = RescheduleLogicHelper.parseProposalUuid(proposalId)
        val now = timeProvider()

        val notificationData = transaction {
            val proposalRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .forUpdate()
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy đề xuất đổi lịch")

            val exchangeUuid = proposalRow[RescheduleProposalsTable.exchangeId]
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            val proposerId = proposalRow[RescheduleProposalsTable.proposerId]
            val currentStatus = proposalRow[RescheduleProposalsTable.status]

            RescheduleLogicHelper.validateParticipant(callerUserId, senderId, receiverId)

            val canProceed = RescheduleLogicHelper.validateRejectEligibility(callerUserId, proposerId, currentStatus)
            if (!canProceed) {
                return@transaction RejectNotificationData(
                    dto = proposalRow.toDto(),
                    shouldNotify = false,
                    proposerId = proposerId,
                    callerName = "",
                    exchangeId = exchangeUuid.toString()
                )
            }

            RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq proposalUuid }) {
                it[status] = RescheduleStatus.REJECTED.name
                it[respondedAt] = now
                it[updatedAt] = now
            }

            val updatedRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .first()

            val callerName = getUserName(callerUserId)
            RejectNotificationData(
                dto = updatedRow.toDto(),
                shouldNotify = true,
                proposerId = proposerId,
                callerName = callerName,
                exchangeId = exchangeUuid.toString()
            )
        }

        if (notificationData.shouldNotify) {
            try {
                notificationService?.createNotification(
                    userId = notificationData.proposerId,
                    type = "RESCHEDULE_REJECTED",
                    title = "Từ chối đổi lịch hẹn",
                    body = RescheduleLogicHelper.formatRejectedNotificationBody(notificationData.callerName),
                    entityId = notificationData.exchangeId,
                    dataPayload = RescheduleLogicHelper.formatRescheduleDataPayload(notificationData.exchangeId, notificationData.dto.id)
                )
            } catch (e: Throwable) {
                logger.error("Lỗi khi gửi thông báo RESCHEDULE_REJECTED: ${e.message}", e)
            }
        }

        return notificationData.dto
    }

    open fun cancelProposal(callerUserId: String, proposalId: String): RescheduleProposalDto {
        val proposalUuid = RescheduleLogicHelper.parseProposalUuid(proposalId)
        val now = timeProvider()

        return transaction {
            val proposalRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .forUpdate()
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy đề xuất đổi lịch")

            val exchangeUuid = proposalRow[RescheduleProposalsTable.exchangeId]
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .firstOrNull() ?: throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            val proposerId = proposalRow[RescheduleProposalsTable.proposerId]
            val currentStatus = proposalRow[RescheduleProposalsTable.status]

            RescheduleLogicHelper.validateParticipant(callerUserId, senderId, receiverId)

            val canProceed = RescheduleLogicHelper.validateCancelEligibility(callerUserId, proposerId, currentStatus)
            if (!canProceed) {
                return@transaction proposalRow.toDto()
            }

            RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq proposalUuid }) {
                it[status] = RescheduleStatus.CANCELLED.name
                it[respondedAt] = now
                it[updatedAt] = now
            }

            val updatedRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .first()

            updatedRow.toDto()
        }
    }

    open fun cleanupExpiredProposals(now: LocalDateTime = timeProvider()): Int = transaction {
        val threshold24h = now.minusHours(24)

        // Quét các proposal PENDING quá 24h hoặc có new_scheduled_at <= now
        val expiredRows = RescheduleProposalsTable.selectAll()
            .where {
                (RescheduleProposalsTable.status eq RescheduleStatus.PENDING.name) and
                (
                    (RescheduleProposalsTable.createdAt lessEq threshold24h) or
                    (RescheduleProposalsTable.newScheduledAt lessEq now)
                )
            }
            .toList()

        if (expiredRows.isEmpty()) return@transaction 0

        val expiredIds = expiredRows.map { it[RescheduleProposalsTable.id] }
        RescheduleProposalsTable.update({ RescheduleProposalsTable.id inList expiredIds }) {
            it[status] = RescheduleStatus.EXPIRED.name
            it[updatedAt] = now
        }

        expiredRows.size
    }

    private fun getUserName(userId: String): String {
        return ProfilesTable
            .selectAll()
            .where { ProfilesTable.userId eq userId }
            .firstOrNull()
            ?.get(ProfilesTable.fullName)
            ?.ifBlank { "Đối tác" }
            ?: "Đối tác"
    }

    private fun ResultRow.toDto(): RescheduleProposalDto {
        return RescheduleProposalDto(
            id = this[RescheduleProposalsTable.id].toString(),
            exchangeId = this[RescheduleProposalsTable.exchangeId].toString(),
            proposerId = this[RescheduleProposalsTable.proposerId],
            newScheduledAt = this[RescheduleProposalsTable.newScheduledAt].format(DateTimeFormatter.ISO_DATE_TIME),
            reason = this[RescheduleProposalsTable.reason],
            status = this[RescheduleProposalsTable.status],
            proposalNumber = this[RescheduleProposalsTable.proposalNumber],
            respondedAt = this[RescheduleProposalsTable.respondedAt]?.format(DateTimeFormatter.ISO_DATE_TIME),
            createdAt = this[RescheduleProposalsTable.createdAt].format(DateTimeFormatter.ISO_DATE_TIME),
            updatedAt = this[RescheduleProposalsTable.updatedAt].format(DateTimeFormatter.ISO_DATE_TIME)
        )
    }

    private fun isUniquePendingRescheduleViolation(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current is java.sql.SQLException && current.sqlState == "23505") {
                return true
            }
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("idx_unique_pending_reschedule") || msg.contains("23505")) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
