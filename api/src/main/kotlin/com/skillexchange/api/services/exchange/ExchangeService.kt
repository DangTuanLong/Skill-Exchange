package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.models.exchange.ExchangeRequestDto
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.ValidationException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

open class ExchangeService(
    private val notificationHook: IExchangeNotificationHook = NoOpExchangeNotificationHook()
) {

    /**
     * Tạo yêu cầu trao đổi kỹ năng mới (sender -> receiver).
     */
    open fun createExchangeRequest(senderId: String, req: CreateExchangeRequest): ExchangeRequestDto = transaction {
        // 1. Kiểm tra sender != receiver
        if (senderId == req.receiverId) {
            throw ValidationException("Không thể gửi yêu cầu trao đổi cho chính mình")
        }

        // 2. Kiểm tra receiver tồn tại
        val receiverProfile = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq req.receiverId }
            .firstOrNull() ?: throw ValidationException("Người nhận không tồn tại")

        // 3. Kiểm tra durationMinutes
        val allowedDurations = listOf(30, 60, 90, 120)
        if (req.durationMinutes !in allowedDurations) {
            throw ValidationException("Thời lượng trao đổi phải là 30, 60, 90 hoặc 120 phút")
        }

        // 4. Kiểm tra meetingMode
        val allowedModes = listOf("ONLINE", "IN_PERSON", "UNDECIDED")
        val upperMode = req.meetingMode.uppercase().trim()
        if (upperMode !in allowedModes) {
            throw ValidationException("Chế độ gặp gỡ không hợp lệ (phải là ONLINE, IN_PERSON hoặc UNDECIDED)")
        }

        // 5. Kiểm tra scheduledAt ở tương lai
        val scheduledDateTime = parseIsoDateTime(req.scheduledAt)
        if (!scheduledDateTime.isAfter(LocalDateTime.now())) {
            throw ValidationException("Thời gian hẹn phải ở tương lai")
        }

        // 6. Kiểm tra cặp kỹ năng hợp lệ hai chiều (BUSINESS_RULES.md §3)
        // Chiều 1: Sender dạy skillOfferedId cho Receiver (Sender HAVE >= Receiver WANT)
        val senderHave = UserSkillsTable.selectAll()
            .where {
                (UserSkillsTable.userId eq senderId) and
                (UserSkillsTable.skillId eq req.skillOfferedId) and
                (UserSkillsTable.type eq "HAVE")
            }.firstOrNull() ?: throw ValidationException("Người gửi không có kỹ năng dạy được chọn (skill_offered_id)")

        val receiverWant = UserSkillsTable.selectAll()
            .where {
                (UserSkillsTable.userId eq req.receiverId) and
                (UserSkillsTable.skillId eq req.skillOfferedId) and
                (UserSkillsTable.type eq "WANT")
            }.firstOrNull() ?: throw ValidationException("Người nhận không có nhu cầu học kỹ năng bạn đề xuất dạy")

        if (senderHave[UserSkillsTable.proficiencyLevel] < receiverWant[UserSkillsTable.proficiencyLevel]) {
            throw ValidationException(
                "Trình độ kỹ năng dạy của bạn (${senderHave[UserSkillsTable.proficiencyLevel]}) chưa đạt mức mục tiêu người nhận cần học (${receiverWant[UserSkillsTable.proficiencyLevel]})"
            )
        }

        // Chiều 2: Receiver dạy skillWantedId cho Sender (Receiver HAVE >= Sender WANT)
        val receiverHave = UserSkillsTable.selectAll()
            .where {
                (UserSkillsTable.userId eq req.receiverId) and
                (UserSkillsTable.skillId eq req.skillWantedId) and
                (UserSkillsTable.type eq "HAVE")
            }.firstOrNull() ?: throw ValidationException("Người nhận không có kỹ năng bạn muốn học (skill_wanted_id)")

        val senderWant = UserSkillsTable.selectAll()
            .where {
                (UserSkillsTable.userId eq senderId) and
                (UserSkillsTable.skillId eq req.skillWantedId) and
                (UserSkillsTable.type eq "WANT")
            }.firstOrNull() ?: throw ValidationException("Người gửi không có kỹ năng muốn học trong hồ sơ của mình")

        if (receiverHave[UserSkillsTable.proficiencyLevel] < senderWant[UserSkillsTable.proficiencyLevel]) {
            throw ValidationException(
                "Trình độ kỹ năng của người nhận (${receiverHave[UserSkillsTable.proficiencyLevel]}) chưa đạt mức mục tiêu bạn muốn học (${senderWant[UserSkillsTable.proficiencyLevel]})"
            )
        }

        // 7. Insert vào database
        val now = LocalDateTime.now()
        val newId = UUID.randomUUID()

        ExchangeRequestsTable.insert {
            it[id]                  = newId
            it[this.senderId]       = senderId
            it[this.receiverId]     = req.receiverId
            it[skillOfferedId]      = req.skillOfferedId
            it[skillWantedId]       = req.skillWantedId
            it[status]              = ExchangeStatus.PENDING.name
            it[durationMinutes]     = req.durationMinutes
            it[meetingMode]         = upperMode
            it[message]             = req.message?.trim().takeUnless { it.isNullOrBlank() }
            it[scheduledAt]         = scheduledDateTime
            it[senderCompletedAt]   = null
            it[receiverCompletedAt] = null
            it[createdAt]           = now
            it[updatedAt]           = now
        }

        val dto = getExchangeRequestInternal(newId.toString())
            ?: throw IllegalStateException("Không thể tải yêu cầu vừa tạo")

        notificationHook.onRequestCreated(dto)
        dto
    }

    /**
     * Danh sách yêu cầu gửi đến người gọi (receiverId = userId).
     */
    open fun getIncomingRequests(userId: String): List<ExchangeRequestDto> = transaction {
        val rows = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.receiverId eq userId }
            .orderBy(ExchangeRequestsTable.scheduledAt to SortOrder.ASC)
            .toList()

        buildDtos(rows)
    }

    /**
     * Danh sách yêu cầu người gọi đã gửi (senderId = userId).
     */
    open fun getOutgoingRequests(userId: String): List<ExchangeRequestDto> = transaction {
        val rows = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.senderId eq userId }
            .orderBy(ExchangeRequestsTable.createdAt to SortOrder.DESC)
            .toList()

        buildDtos(rows)
    }

    /**
     * Chi tiết một yêu cầu trao đổi.
     * Trả về null nếu không tìm thấy; ném ExchangeForbiddenException nếu caller không phải thành viên.
     */
    open fun getExchangeRequest(callerUserId: String, exchangeId: String): ExchangeRequestDto? = transaction {
        val uuid = try {
            UUID.fromString(exchangeId)
        } catch (e: Exception) {
            return@transaction null
        }

        val row = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.id eq uuid }
            .firstOrNull() ?: return@transaction null

        val sId = row[ExchangeRequestsTable.senderId]
        val rId = row[ExchangeRequestsTable.receiverId]

        if (callerUserId != sId && callerUserId != rId) {
            throw ExchangeForbiddenException("Bạn không phải thành viên tham gia yêu cầu này")
        }

        val dto = buildDto(row)
        if (dto != null && (dto.status == ExchangeStatus.ACCEPTED.name || dto.status == ExchangeStatus.COMPLETED.name)) {
            notificationHook.onRequestAccepted(dto)
        }
        dto
    }

    /**
     * Đồng bộ phòng chat Firestore cho tất cả yêu cầu đã ACCEPTED khi máy chủ khởi động (idempotent).
     */
    open fun syncExistingAcceptedExchanges() = transaction {
        val rows = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.status eq ExchangeStatus.ACCEPTED.name }
            .toList()
        val dtos = buildDtos(rows)
        dtos.forEach { dto ->
            notificationHook.onRequestAccepted(dto)
        }
    }

    /**
     * Chấp nhận yêu cầu (chỉ receiver).
     */
    open fun acceptRequest(callerUserId: String, exchangeId: String): ExchangeRequestDto? = transaction {
        val uuid = try { UUID.fromString(exchangeId) } catch (e: Exception) { return@transaction null }
        val row = ExchangeRequestsTable.selectAll().where { ExchangeRequestsTable.id eq uuid }.firstOrNull()
            ?: return@transaction null

        val currentStatus = ExchangeStatus.valueOf(row[ExchangeRequestsTable.status])
        val sId = row[ExchangeRequestsTable.senderId]
        val rId = row[ExchangeRequestsTable.receiverId]
        val sCompleted = row[ExchangeRequestsTable.senderCompletedAt]
        val rCompleted = row[ExchangeRequestsTable.receiverCompletedAt]
        val reason = row[ExchangeRequestsTable.cancellationReason]
        val currentAcceptedAt = row[ExchangeRequestsTable.acceptedAt]

        val transition = ExchangeStateMachine.accept(
            currentStatus = currentStatus,
            senderId = sId,
            receiverId = rId,
            callerUserId = callerUserId,
            senderCompletedAt = sCompleted,
            receiverCompletedAt = rCompleted,
            cancellationReason = reason,
            currentAcceptedAt = currentAcceptedAt
        )

        if (transition.isChanged) {
            ExchangeRequestsTable.update({ ExchangeRequestsTable.id eq uuid }) {
                it[status]     = transition.newStatus.name
                it[acceptedAt] = transition.acceptedAt
                it[updatedAt]  = LocalDateTime.now()
            }
        }

        val updatedDto = getExchangeRequestInternal(exchangeId)!!
        if (transition.isChanged) {
            notificationHook.onRequestAccepted(updatedDto)
        }
        updatedDto
    }

    /**
     * Từ chối yêu cầu (chỉ receiver).
     */
    open fun rejectRequest(callerUserId: String, exchangeId: String): ExchangeRequestDto? = transaction {
        val uuid = try { UUID.fromString(exchangeId) } catch (e: Exception) { return@transaction null }
        val row = ExchangeRequestsTable.selectAll().where { ExchangeRequestsTable.id eq uuid }.firstOrNull()
            ?: return@transaction null

        val currentStatus = ExchangeStatus.valueOf(row[ExchangeRequestsTable.status])
        val sId = row[ExchangeRequestsTable.senderId]
        val rId = row[ExchangeRequestsTable.receiverId]
        val sCompleted = row[ExchangeRequestsTable.senderCompletedAt]
        val rCompleted = row[ExchangeRequestsTable.receiverCompletedAt]
        val reason = row[ExchangeRequestsTable.cancellationReason]

        val transition = ExchangeStateMachine.reject(
            currentStatus = currentStatus,
            senderId = sId,
            receiverId = rId,
            callerUserId = callerUserId,
            senderCompletedAt = sCompleted,
            receiverCompletedAt = rCompleted,
            cancellationReason = reason
        )

        if (transition.isChanged) {
            ExchangeRequestsTable.update({ ExchangeRequestsTable.id eq uuid }) {
                it[status]    = transition.newStatus.name
                it[updatedAt] = LocalDateTime.now()
            }
        }

        val updatedDto = getExchangeRequestInternal(exchangeId)!!
        if (transition.isChanged) {
            notificationHook.onRequestRejected(updatedDto)
        }
        updatedDto
    }

    /**
     * Hủy yêu cầu: sender khi PENDING; cả 2 khi ACCEPTED.
     */
    open fun cancelRequest(callerUserId: String, exchangeId: String, reason: String?): ExchangeRequestDto? = transaction {
        val uuid = try { UUID.fromString(exchangeId) } catch (e: Exception) { return@transaction null }
        val row = ExchangeRequestsTable.selectAll().where { ExchangeRequestsTable.id eq uuid }.firstOrNull()
            ?: return@transaction null

        val currentStatus = ExchangeStatus.valueOf(row[ExchangeRequestsTable.status])
        val sId = row[ExchangeRequestsTable.senderId]
        val rId = row[ExchangeRequestsTable.receiverId]
        val sCompleted = row[ExchangeRequestsTable.senderCompletedAt]
        val rCompleted = row[ExchangeRequestsTable.receiverCompletedAt]
        val existingReason = row[ExchangeRequestsTable.cancellationReason]
        val currentAcceptedAt = row[ExchangeRequestsTable.acceptedAt]

        val transition = ExchangeStateMachine.cancel(
            currentStatus = currentStatus,
            senderId = sId,
            receiverId = rId,
            callerUserId = callerUserId,
            reason = reason,
            senderCompletedAt = sCompleted,
            receiverCompletedAt = rCompleted,
            existingReason = existingReason,
            currentAcceptedAt = currentAcceptedAt
        )

        if (transition.isChanged) {
            ExchangeRequestsTable.update({ ExchangeRequestsTable.id eq uuid }) {
                it[status]             = transition.newStatus.name
                it[cancellationReason] = transition.cancellationReason
                it[updatedAt]          = LocalDateTime.now()
            }
        }

        val updatedDto = getExchangeRequestInternal(exchangeId)!!
        if (transition.isChanged) {
            notificationHook.onRequestCancelled(updatedDto, callerUserId)
        }
        updatedDto
    }

    /**
     * Xác nhận hoàn thành: mỗi người ấn xác nhận, cả hai xác nhận thì chuyển sang COMPLETED.
     */
    open fun completeRequest(callerUserId: String, exchangeId: String): ExchangeRequestDto? = transaction {
        val uuid = try { UUID.fromString(exchangeId) } catch (e: Exception) { return@transaction null }
        val row = ExchangeRequestsTable.selectAll().where { ExchangeRequestsTable.id eq uuid }.firstOrNull()
            ?: return@transaction null

        val currentStatus = ExchangeStatus.valueOf(row[ExchangeRequestsTable.status])
        val sId = row[ExchangeRequestsTable.senderId]
        val rId = row[ExchangeRequestsTable.receiverId]
        val sCompleted = row[ExchangeRequestsTable.senderCompletedAt]
        val rCompleted = row[ExchangeRequestsTable.receiverCompletedAt]
        val reason = row[ExchangeRequestsTable.cancellationReason]
        val currentAcceptedAt = row[ExchangeRequestsTable.acceptedAt]

        val transition = ExchangeStateMachine.complete(
            currentStatus = currentStatus,
            senderId = sId,
            receiverId = rId,
            callerUserId = callerUserId,
            now = LocalDateTime.now(),
            senderCompletedAt = sCompleted,
            receiverCompletedAt = rCompleted,
            cancellationReason = reason,
            currentAcceptedAt = currentAcceptedAt
        )

        if (transition.isChanged) {
            ExchangeRequestsTable.update({ ExchangeRequestsTable.id eq uuid }) {
                it[status]              = transition.newStatus.name
                it[senderCompletedAt]   = transition.senderCompletedAt
                it[receiverCompletedAt] = transition.receiverCompletedAt
                it[updatedAt]           = LocalDateTime.now()
            }
        }

        val updatedDto = getExchangeRequestInternal(exchangeId)!!
        if (transition.newStatus == ExchangeStatus.COMPLETED && transition.isChanged) {
            notificationHook.onRequestCompleted(updatedDto)
        }
        updatedDto
    }

    // ─────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────

    private fun getExchangeRequestInternal(exchangeId: String): ExchangeRequestDto? {
        val uuid = try { UUID.fromString(exchangeId) } catch (e: Exception) { return null }
        val row = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.id eq uuid }
            .firstOrNull() ?: return null
        return buildDto(row)
    }

    private fun buildDto(row: ResultRow): ExchangeRequestDto {
        return buildDtos(listOf(row)).first()
    }

    private fun buildDtos(rows: List<ResultRow>): List<ExchangeRequestDto> {
        if (rows.isEmpty()) return emptyList()

        val skillIds = rows.flatMap { listOf(it[ExchangeRequestsTable.skillOfferedId], it[ExchangeRequestsTable.skillWantedId]) }.distinct()
        val skillsMap = SkillsTable.selectAll()
            .where { SkillsTable.id inList skillIds }
            .associate { it[SkillsTable.id] to it[SkillsTable.name] }

        val userIds = rows.flatMap { listOf(it[ExchangeRequestsTable.senderId], it[ExchangeRequestsTable.receiverId]) }.distinct()
        val profilesMap = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList userIds }
            .associateBy { it[ProfilesTable.userId] }

        return rows.map { r ->
            val sId = r[ExchangeRequestsTable.senderId]
            val rId = r[ExchangeRequestsTable.receiverId]
            val sProfile = profilesMap[sId]
            val rProfile = profilesMap[rId]
            val offeredId = r[ExchangeRequestsTable.skillOfferedId]
            val wantedId = r[ExchangeRequestsTable.skillWantedId]

            ExchangeRequestDto(
                id = r[ExchangeRequestsTable.id].toString(),
                senderId = sId,
                receiverId = rId,
                senderName = sProfile?.get(ProfilesTable.fullName),
                senderAvatarUrl = sProfile?.get(ProfilesTable.avatarUrl),
                receiverName = rProfile?.get(ProfilesTable.fullName),
                receiverAvatarUrl = rProfile?.get(ProfilesTable.avatarUrl),
                skillOfferedId = offeredId,
                skillOfferedName = skillsMap[offeredId] ?: "Kỹ năng #$offeredId",
                skillWantedId = wantedId,
                skillWantedName = skillsMap[wantedId] ?: "Kỹ năng #$wantedId",
                status = r[ExchangeRequestsTable.status],
                durationMinutes = r[ExchangeRequestsTable.durationMinutes],
                meetingMode = r[ExchangeRequestsTable.meetingMode],
                message = r[ExchangeRequestsTable.message],
                cancellationReason = r[ExchangeRequestsTable.cancellationReason],
                scheduledAt = r[ExchangeRequestsTable.scheduledAt].toString(),
                acceptedAt = r[ExchangeRequestsTable.acceptedAt]?.toString(),
                senderCompletedAt = r[ExchangeRequestsTable.senderCompletedAt]?.toString(),
                receiverCompletedAt = r[ExchangeRequestsTable.receiverCompletedAt]?.toString(),
                createdAt = r[ExchangeRequestsTable.createdAt].toString(),
                updatedAt = r[ExchangeRequestsTable.updatedAt].toString()
            )
        }
    }

    private fun parseIsoDateTime(str: String): LocalDateTime {
        return try {
            LocalDateTime.parse(str)
        } catch (e: Exception) {
            try {
                java.time.OffsetDateTime.parse(str).toLocalDateTime()
            } catch (e2: Exception) {
                try {
                    java.time.Instant.parse(str).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime()
                } catch (e3: Exception) {
                    throw ValidationException("Định dạng scheduled_at không hợp lệ. Vui lòng dùng chuẩn ISO-8601 (ví dụ: 2026-10-15T19:00:00)")
                }
            }
        }
    }
}
