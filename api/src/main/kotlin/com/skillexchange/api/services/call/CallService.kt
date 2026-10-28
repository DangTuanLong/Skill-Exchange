package com.skillexchange.api.services.call

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.call.*
import com.skillexchange.api.models.db.CallSessionsTable
import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.services.chat.IFirestoreChatService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

open class CallForbiddenException(message: String, val errorCode: String = ErrorCodes.FORBIDDEN) : RuntimeException(message)
open class CallConflictException(message: String, val errorCode: String = ErrorCodes.CONFLICT) : RuntimeException(message)
open class CallNotFoundException(message: String, val errorCode: String = ErrorCodes.CALL_NOT_FOUND) : RuntimeException(message)
open class CallBusyException(message: String, val errorCode: String = ErrorCodes.USER_BUSY) : RuntimeException(message)
open class CallRateLimitException(message: String, val errorCode: String = ErrorCodes.CALL_COOLDOWN_ACTIVE) : RuntimeException(message)

open class CallService(
    private val liveKitTokenService: LiveKitTokenService,
    private val firestoreChatService: IFirestoreChatService? = null,
    private val deviceService: com.skillexchange.api.services.device.DeviceService? = null,
    private val fcmService: com.skillexchange.api.services.notification.FcmService? = null
) {
    private val logger = LoggerFactory.getLogger(CallService::class.java)

    // Rate Limiting In-Memory (5 req/phút/user)
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    // Cooldown 10s giữa 2 lần bấm gọi
    private val lastCallInitiated = ConcurrentHashMap<String, Long>()

    private fun checkRateLimit(userId: String) {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps.computeIfAbsent(userId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > 60_000L }
            if (timestamps.size >= 5) {
                throw CallRateLimitException("Bạn đã yêu cầu tạo cuộc gọi quá nhiều lần. Vui lòng đợi 1 phút.", ErrorCodes.RATE_LIMITED)
            }
            timestamps.add(now)
        }

        val lastTime = lastCallInitiated[userId]
        if (lastTime != null && now - lastTime < 10_000L) {
            val remainSeconds = ((10_000L - (now - lastTime)) / 1000L) + 1
            throw CallRateLimitException("Vui lòng đợi $remainSeconds giây trước khi bấm gọi lại.", ErrorCodes.CALL_COOLDOWN_ACTIVE)
        }
    }

    /**
     * Bắt đầu cuộc gọi (Caller xin cấp token phòng mới)
     */
    open fun initiateCall(callerId: String, req: CallTokenRequest): CallTokenResponse {
        val callTypeEnum = CallType.fromStringOrNull(req.callType)
            ?: throw ValidationException("Loại cuộc gọi không hợp lệ (phải là VOICE hoặc VIDEO)")

        val exchangeUuid = try {
            UUID.fromString(req.exchangeId)
        } catch (_: Exception) {
            throw ValidationException("exchangeId không hợp lệ")
        }

        checkRateLimit(callerId)

        val (result, targetReceiverId, callerName) = transaction {
            // 1. Kiểm tra exchange tồn tại & quyền thành viên
            val exchangeRow = ExchangeRequestsTable.selectAll()
                .where { ExchangeRequestsTable.id eq exchangeUuid }
                .firstOrNull() ?: throw CallNotFoundException("Không tìm thấy yêu cầu trao đổi")

            val senderId = exchangeRow[ExchangeRequestsTable.senderId]
            val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]
            val status = exchangeRow[ExchangeRequestsTable.status]

            if (callerId != senderId && callerId != receiverId) {
                throw CallForbiddenException("Bạn không phải thành viên tham gia trao đổi này", ErrorCodes.NOT_PARTICIPANT)
            }

            if (status != ExchangeStatus.ACCEPTED.name) {
                throw CallConflictException("Yêu cầu trao đổi chưa được chấp nhận hoặc đã kết thúc", ErrorCodes.EXCHANGE_NOT_ACTIVE)
            }

            val targetReceiverId = if (callerId == senderId) receiverId else senderId

            // 2. Kiểm tra giới hạn gọi dồn dập (Anti-spam): Tối đa 3 lần bị MISSED/REJECTED trong 2 phút
            val twoMinutesAgo = LocalDateTime.now().minusMinutes(2)
            val spamCount = CallSessionsTable.selectAll()
                .where {
                    (CallSessionsTable.callerId eq callerId) and
                    (CallSessionsTable.receiverId eq targetReceiverId) and
                    (CallSessionsTable.createdAt greaterEq twoMinutesAgo) and
                    (CallSessionsTable.status inList listOf(CallStatus.MISSED.name, CallStatus.REJECTED.name))
                }.count()

            if (spamCount >= 3) {
                throw CallRateLimitException(
                    "Bạn đã gọi 3 lần không thành công tới đối tác này. Vui lòng đợi 2 phút trước khi thử lại.",
                    ErrorCodes.CALL_COOLDOWN_ACTIVE
                )
            }

            // 3. Concurrency Guard với Ordered Locking (ngăn Race Condition A gọi B và B gọi A cùng lúc)
            val (firstId, secondId) = if (callerId < targetReceiverId) callerId to targetReceiverId else targetReceiverId to callerId

            // Khóa thứ tự 2 user bằng SELECT FOR UPDATE trên ProfilesTable
            ProfilesTable.selectAll()
                .where { (ProfilesTable.userId eq firstId) or (ProfilesTable.userId eq secondId) }
                .orderBy(ProfilesTable.userId to SortOrder.ASC)
                .forUpdate()
                .toList()

            // 4. Lazy Timeout: dọn dẹp các cuộc gọi quá hạn của 2 user trước khi check BUSY
            val now = LocalDateTime.now()
            val thirtySecondsAgo = now.minusSeconds(30)
            val twoHoursAgo = now.minusHours(2)

            CallSessionsTable.update({
                ((CallSessionsTable.callerId inList listOf(firstId, secondId)) or (CallSessionsTable.receiverId inList listOf(firstId, secondId))) and
                (CallSessionsTable.status inList listOf(CallStatus.DIALING.name, CallStatus.RINGING.name)) and
                (CallSessionsTable.createdAt less thirtySecondsAgo)
            }) {
                it[CallSessionsTable.status] = CallStatus.MISSED.name
                it[endedAt] = now
                it[updatedAt] = now
            }

            CallSessionsTable.update({
                ((CallSessionsTable.callerId inList listOf(firstId, secondId)) or (CallSessionsTable.receiverId inList listOf(firstId, secondId))) and
                (CallSessionsTable.status eq CallStatus.CONNECTED.name) and
                (CallSessionsTable.connectedAt less twoHoursAgo)
            }) {
                it[CallSessionsTable.status] = CallStatus.ENDED.name
                it[endedAt] = now
                it[durationSeconds] = 7200
                it[updatedAt] = now
            }

            // 5. Kiểm tra xem caller hoặc receiver có cuộc gọi nào đang active không
            val activeCalls = CallSessionsTable.selectAll()
                .where {
                    ((CallSessionsTable.callerId inList listOf(callerId, targetReceiverId)) or
                     (CallSessionsTable.receiverId inList listOf(callerId, targetReceiverId))) and
                    (CallSessionsTable.status inList CallStatus.activeStatuses())
                }.toList()

            if (activeCalls.isNotEmpty()) {
                throw CallBusyException("Đối tác hoặc bạn hiện đang trong một cuộc gọi khác", ErrorCodes.USER_BUSY)
            }

            // 6. Lấy tên người gọi để nhúng vào LiveKit JWT
            val callerProfile = ProfilesTable.selectAll()
                .where { ProfilesTable.userId eq callerId }
                .firstOrNull()
            val callerName = callerProfile?.get(ProfilesTable.fullName)?.ifBlank { "Đối tác" } ?: "Đối tác"

            // 7. Tạo phiên gọi mới
            val newSessionId = UUID.randomUUID()
            val roomName = liveKitTokenService.generateRoomName()

            CallSessionsTable.insert {
                it[CallSessionsTable.id] = newSessionId
                it[CallSessionsTable.exchangeId] = exchangeUuid
                it[CallSessionsTable.callerId] = callerId
                it[CallSessionsTable.receiverId] = targetReceiverId
                it[CallSessionsTable.roomName] = roomName
                it[CallSessionsTable.callType] = callTypeEnum.name
                it[CallSessionsTable.status] = CallStatus.DIALING.name
                it[CallSessionsTable.startedAt] = now
                it[CallSessionsTable.durationSeconds] = 0
                it[CallSessionsTable.createdAt] = now
                it[CallSessionsTable.updatedAt] = now
            }

            // Ghi nhận cooldown sau khi insert thành công
            lastCallInitiated[callerId] = System.currentTimeMillis()

            val token = liveKitTokenService.createJoinToken(callerId, callerName, roomName)

            val resp = CallTokenResponse(
                url = liveKitTokenService.getServerUrl(),
                roomName = roomName,
                token = token,
                callType = callTypeEnum.name,
                sessionId = newSessionId.toString()
            )
            Triple(resp, targetReceiverId, callerName)
        }

        // Gửi FCM High-Priority Data Push báo cuộc gọi đến (không chứa token hay dữ liệu nhạy cảm)
        try {
            val tokens = deviceService?.getTokensForUser(targetReceiverId).orEmpty()
            if (tokens.isNotEmpty() && fcmService != null) {
                val callData = mapOf(
                    "type" to "INCOMING_CALL",
                    "sessionId" to result.sessionId,
                    "exchangeId" to req.exchangeId,
                    "callerId" to callerId,
                    "callerName" to callerName,
                    "callType" to result.callType
                )
                tokens.forEach { token ->
                    fcmService.sendDataPushAsync(token, callData, priority = "HIGH")
                }
            }
        } catch (e: Throwable) {
            logger.error("Lỗi khi gửi FCM incoming call push cho receiver $targetReceiverId: ${e.message}")
        }

        return result
    }

    /**
     * Tham gia phòng cuộc gọi (Receiver lấy token tham gia phòng đã được khởi tạo)
     */
    open fun joinCall(receiverId: String, sessionId: String): CallTokenResponse = transaction {
        val sessionUuid = try { UUID.fromString(sessionId) } catch (_: Exception) {
            throw ValidationException("sessionId không hợp lệ")
        }

        val row = CallSessionsTable.selectAll()
            .where { CallSessionsTable.id eq sessionUuid }
            .firstOrNull() ?: throw CallNotFoundException("Không tìm thấy phiên cuộc gọi")

        val sId = row[CallSessionsTable.callerId]
        val rId = row[CallSessionsTable.receiverId]
        val status = row[CallSessionsTable.status]
        val roomName = row[CallSessionsTable.roomName]
        val callType = row[CallSessionsTable.callType]

        if (receiverId != sId && receiverId != rId) {
            throw CallForbiddenException("Bạn không phải thành viên của cuộc gọi này", ErrorCodes.NOT_PARTICIPANT)
        }

        if (status in CallStatus.terminalStatuses()) {
            throw CallConflictException("Cuộc gọi đã kết thúc", ErrorCodes.CONFLICT)
        }

        // Cập nhật DIALING -> RINGING khi receiver fetch token
        if (status == CallStatus.DIALING.name && receiverId == rId) {
            CallSessionsTable.update({ CallSessionsTable.id eq sessionUuid }) {
                it[CallSessionsTable.status] = CallStatus.RINGING.name
                it[updatedAt] = LocalDateTime.now()
            }
        }

        val profile = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq receiverId }
            .firstOrNull()
        val userName = profile?.get(ProfilesTable.fullName)?.ifBlank { "Đối tác" } ?: "Đối tác"

        val token = liveKitTokenService.createJoinToken(receiverId, userName, roomName)

        CallTokenResponse(
            url = liveKitTokenService.getServerUrl(),
            roomName = roomName,
            token = token,
            callType = callType,
            sessionId = sessionId
        )
    }

    /**
     * Xác nhận đã kết nối thành công (Receiver gọi khi nhấc máy) - Idempotent
     */
    open fun acceptConnected(userId: String, sessionId: String): CallSessionDto = transaction {
        val sessionUuid = try { UUID.fromString(sessionId) } catch (_: Exception) {
            throw ValidationException("sessionId không hợp lệ")
        }

        val row = CallSessionsTable.selectAll()
            .where { CallSessionsTable.id eq sessionUuid }
            .forUpdate()
            .firstOrNull() ?: throw CallNotFoundException("Không tìm thấy phiên cuộc gọi")

        val rId = row[CallSessionsTable.receiverId]
        if (userId != rId) {
            throw CallForbiddenException("Chỉ người nhận mới có quyền xác nhận kết nối cuộc gọi", ErrorCodes.FORBIDDEN)
        }

        val currentStatus = row[CallSessionsTable.status]
        if (currentStatus == CallStatus.CONNECTED.name) {
            return@transaction row.toDto()
        }

        if (currentStatus in CallStatus.terminalStatuses()) {
            throw CallConflictException("Cuộc gọi đã kết thúc, không thể kết nối", ErrorCodes.CONFLICT)
        }

        val now = LocalDateTime.now()
        CallSessionsTable.update({ CallSessionsTable.id eq sessionUuid }) {
            it[status] = CallStatus.CONNECTED.name
            it[connectedAt] = now
            it[updatedAt] = now
        }

        val updatedRow = CallSessionsTable.selectAll().where { CallSessionsTable.id eq sessionUuid }.first()
        updatedRow.toDto()
    }

    /**
     * Từ chối cuộc gọi (Chỉ Receiver) - Idempotent
     */
    open fun rejectCall(userId: String, sessionId: String): CallSessionDto {
        val (dto, exchangeId) = transaction {
            val sessionUuid = try { UUID.fromString(sessionId) } catch (_: Exception) {
                throw ValidationException("sessionId không hợp lệ")
            }

            val row = CallSessionsTable.selectAll()
                .where { CallSessionsTable.id eq sessionUuid }
                .forUpdate()
                .firstOrNull() ?: throw CallNotFoundException("Không tìm thấy phiên cuộc gọi")

            val rId = row[CallSessionsTable.receiverId]
            if (userId != rId) {
                throw CallForbiddenException("Chỉ người nhận mới có quyền từ chối cuộc gọi", ErrorCodes.FORBIDDEN)
            }

            val currentStatus = row[CallSessionsTable.status]
            if (currentStatus in CallStatus.terminalStatuses()) {
                return@transaction row.toDto() to row[CallSessionsTable.exchangeId].toString()
            }

            val now = LocalDateTime.now()
            CallSessionsTable.update({ CallSessionsTable.id eq sessionUuid }) {
                it[status] = CallStatus.REJECTED.name
                it[endedAt] = now
                it[updatedAt] = now
            }

            val updatedRow = CallSessionsTable.selectAll().where { CallSessionsTable.id eq sessionUuid }.first()
            updatedRow.toDto() to updatedRow[CallSessionsTable.exchangeId].toString()
        }

        // Ghi tin nhắn hệ thống SYSTEM_CALL vào Firestore bất đồng bộ (Lỗi chỉ log)
        try {
            firestoreChatService?.recordSystemCallMessageAsync(
                exchangeId = exchangeId,
                callType = dto.callType,
                callStatus = CallStatus.REJECTED.name,
                durationSeconds = 0,
                callerId = dto.callerId,
                receiverId = dto.receiverId
            )
        } catch (e: Throwable) {
            logger.error("Lỗi khi kích hoạt ghi SYSTEM_CALL cho cuộc gọi từ chối $sessionId: ${e.message}")
        }

        return dto
    }

    private data class EndCallInternalResult(
        val dto: CallSessionDto,
        val exchangeId: String,
        val needRecordHistory: Boolean,
        val wasUnconnected: Boolean
    )

    /**
     * Kết thúc cuộc gọi (Caller hoặc Receiver cúp máy) - Idempotent
     */
    open fun endCall(userId: String, sessionId: String): CallSessionDto {
        val result = transaction {
            val sessionUuid = try { UUID.fromString(sessionId) } catch (_: Exception) {
                throw ValidationException("sessionId không hợp lệ")
            }

            val row = CallSessionsTable.selectAll()
                .where { CallSessionsTable.id eq sessionUuid }
                .forUpdate()
                .firstOrNull() ?: throw CallNotFoundException("Không tìm thấy phiên cuộc gọi")

            val sId = row[CallSessionsTable.callerId]
            val rId = row[CallSessionsTable.receiverId]
            if (userId != sId && userId != rId) {
                throw CallForbiddenException("Bạn không phải thành viên của cuộc gọi này", ErrorCodes.NOT_PARTICIPANT)
            }

            val currentStatus = row[CallSessionsTable.status]
            val wasUnconnected = currentStatus in listOf(CallStatus.DIALING.name, CallStatus.RINGING.name)
            if (currentStatus in CallStatus.terminalStatuses()) {
                return@transaction EndCallInternalResult(row.toDto(), row[CallSessionsTable.exchangeId].toString(), false, false)
            }

            val now = LocalDateTime.now()
            val connectedTime = row[CallSessionsTable.connectedAt]
            val duration = if (currentStatus == CallStatus.CONNECTED.name && connectedTime != null) {
                maxOf(0, Duration.between(connectedTime, now).seconds.toInt())
            } else {
                0
            }

            CallSessionsTable.update({ CallSessionsTable.id eq sessionUuid }) {
                it[status] = CallStatus.ENDED.name
                it[endedAt] = now
                it[durationSeconds] = duration
                it[updatedAt] = now
            }

            val updatedRow = CallSessionsTable.selectAll().where { CallSessionsTable.id eq sessionUuid }.first()
            EndCallInternalResult(updatedRow.toDto(), updatedRow[CallSessionsTable.exchangeId].toString(), true, wasUnconnected)
        }

        if (result.needRecordHistory) {
            try {
                firestoreChatService?.recordSystemCallMessageAsync(
                    exchangeId = result.exchangeId,
                    callType = result.dto.callType,
                    callStatus = CallStatus.ENDED.name,
                    durationSeconds = result.dto.durationSeconds,
                    callerId = result.dto.callerId,
                    receiverId = result.dto.receiverId
                )
            } catch (e: Throwable) {
                logger.error("Lỗi khi ghi nhận SYSTEM_CALL cho cuộc gọi kết thúc $sessionId: ${e.message}")
            }
        }

        // Nếu cuộc gọi bị hủy trước khi kết nối (chưa nhấc máy), gửi FCM CALL_CANCELLED cho đối phương
        if (result.wasUnconnected) {
            try {
                val partnerId = if (userId == result.dto.callerId) result.dto.receiverId else result.dto.callerId
                val tokens = deviceService?.getTokensForUser(partnerId).orEmpty()
                if (tokens.isNotEmpty() && fcmService != null) {
                    val cancelData = mapOf(
                        "type" to "CALL_CANCELLED",
                        "sessionId" to sessionId
                    )
                    tokens.forEach { token ->
                        fcmService.sendDataPushAsync(token, cancelData, priority = "HIGH")
                    }
                }
            } catch (e: Throwable) {
                logger.error("Lỗi khi gửi FCM CALL_CANCELLED cho đối tác $sessionId: ${e.message}")
            }
        }

        return result.dto
    }

    /**
     * Dọn dẹp các cuộc gọi bị treo quá hạn (Tích hợp trong ReminderSchedulerService)
     */
    open fun cleanupExpiredCalls(): Int = transaction {
        val now = LocalDateTime.now()
        val thirtySecondsAgo = now.minusSeconds(30)
        val twoHoursAgo = now.minusHours(2)

        // 1. DIALING/RINGING quá 30 giây -> MISSED
        val missedCount = CallSessionsTable.update({
            (CallSessionsTable.status inList listOf(CallStatus.DIALING.name, CallStatus.RINGING.name)) and
            (CallSessionsTable.createdAt less thirtySecondsAgo)
        }) {
            it[status] = CallStatus.MISSED.name
            it[endedAt] = now
            it[updatedAt] = now
        }

        // 2. CONNECTED quá 2 giờ -> ENDED
        val endedCount = CallSessionsTable.update({
            (CallSessionsTable.status eq CallStatus.CONNECTED.name) and
            (CallSessionsTable.connectedAt less twoHoursAgo)
        }) {
            it[status] = CallStatus.ENDED.name
            it[endedAt] = now
            it[durationSeconds] = 7200
            it[updatedAt] = now
        }

        missedCount + endedCount
    }

    private fun ResultRow.toDto(): CallSessionDto {
        return CallSessionDto(
            id = this[CallSessionsTable.id].toString(),
            exchangeId = this[CallSessionsTable.exchangeId].toString(),
            callerId = this[CallSessionsTable.callerId],
            receiverId = this[CallSessionsTable.receiverId],
            roomName = this[CallSessionsTable.roomName],
            callType = this[CallSessionsTable.callType],
            status = this[CallSessionsTable.status],
            startedAt = this[CallSessionsTable.startedAt].toString(),
            connectedAt = this[CallSessionsTable.connectedAt]?.toString(),
            endedAt = this[CallSessionsTable.endedAt]?.toString(),
            durationSeconds = this[CallSessionsTable.durationSeconds]
        )
    }
}
