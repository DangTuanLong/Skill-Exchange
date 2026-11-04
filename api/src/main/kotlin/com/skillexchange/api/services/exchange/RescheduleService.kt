package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.RescheduleProposalsTable
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import com.skillexchange.api.models.exchange.RescheduleProposalDto
import com.skillexchange.api.models.exchange.RescheduleStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

open class RescheduleService(
    private val timeProvider: () -> LocalDateTime = { LocalDateTime.now() }
) {

    open fun createProposal(
        callerUserId: String,
        exchangeId: String,
        req: CreateRescheduleRequest
    ): RescheduleProposalDto {
        val exchangeUuid = RescheduleLogicHelper.parseExchangeUuid(exchangeId)
        val cleanReason = RescheduleLogicHelper.validateReason(req.reason)
        val now = timeProvider()
        val newScheduledAt = RescheduleLogicHelper.validateNewScheduledAt(req.newScheduledAt, now)

        return transaction {
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
            val pStatus = p[RescheduleProposalsTable.status]
            val pCreatedAt = p[RescheduleProposalsTable.createdAt]
            val pNewScheduledAt = p[RescheduleProposalsTable.newScheduledAt]
            val isExpired = RescheduleLogicHelper.isProposalExpired(pStatus, pCreatedAt, pNewScheduledAt, now)

            if (isExpired) {
                RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq p[RescheduleProposalsTable.id] }) {
                    it[status] = RescheduleStatus.EXPIRED.name
                    it[updatedAt] = now
                }
            } else {
                throw RescheduleConflictException(
                    "Đang có đề xuất đổi lịch chờ phản hồi cho phiên trao đổi này.",
                    ErrorCodes.PENDING_PROPOSAL_EXISTS
                )
            }
        }

        val newProposalId = UUID.randomUUID()
        val nextNumber = (totalCount + 1).toInt()

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

        val createdRow = RescheduleProposalsTable.selectAll()
            .where { RescheduleProposalsTable.id eq newProposalId }
            .first()

        createdRow.toDto()
        }
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

        return transaction {
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
                return@transaction proposalRow.toDto()
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

            updatedRow.toDto()
        }
    }

    open fun rejectProposal(callerUserId: String, proposalId: String): RescheduleProposalDto {
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

            val canProceed = RescheduleLogicHelper.validateRejectEligibility(callerUserId, proposerId, currentStatus)
            if (!canProceed) {
                return@transaction proposalRow.toDto()
            }

            RescheduleProposalsTable.update({ RescheduleProposalsTable.id eq proposalUuid }) {
                it[status] = RescheduleStatus.REJECTED.name
                it[respondedAt] = now
                it[updatedAt] = now
            }

            val updatedRow = RescheduleProposalsTable.selectAll()
                .where { RescheduleProposalsTable.id eq proposalUuid }
                .first()

            updatedRow.toDto()
        }
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
}
