package com.skillexchange.api.services.call

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.call.CallStatus
import com.skillexchange.api.models.call.CallTokenRequest
import com.skillexchange.api.models.call.CallType
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.ValidationException
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure logic helper for call operations, decoupled from Exposed ORM transactions.
 */
object CallLogicHelper {

    fun validateInitiateCallRequest(req: CallTokenRequest): Pair<CallType, UUID> {
        val callTypeEnum = CallType.fromStringOrNull(req.callType)
            ?: throw ValidationException("Loại cuộc gọi không hợp lệ (phải là VOICE hoặc VIDEO)")

        val exchangeUuid = try {
            UUID.fromString(req.exchangeId)
        } catch (_: Exception) {
            throw ValidationException("exchangeId không hợp lệ")
        }

        return callTypeEnum to exchangeUuid
    }

    fun parseSessionUuid(sessionId: String): UUID {
        return try {
            UUID.fromString(sessionId)
        } catch (_: Exception) {
            throw ValidationException("sessionId không hợp lệ")
        }
    }

    fun validateParticipant(userId: String, senderId: String, receiverId: String) {
        if (userId != senderId && userId != receiverId) {
            throw CallForbiddenException("Bạn không phải thành viên tham gia trao đổi này", ErrorCodes.NOT_PARTICIPANT)
        }
    }

    fun validateExchangeActive(status: String) {
        if (status != ExchangeStatus.ACCEPTED.name) {
            throw CallConflictException("Yêu cầu trao đổi chưa được chấp nhận hoặc đã kết thúc", ErrorCodes.EXCHANGE_NOT_ACTIVE)
        }
    }

    fun determineTargetReceiverId(callerId: String, senderId: String, receiverId: String): String {
        return if (callerId == senderId) receiverId else senderId
    }

    fun checkSpamLimit(spamCount: Long, maxAllowed: Long = 3) {
        if (spamCount >= maxAllowed) {
            throw CallRateLimitException(
                "Bạn đã gọi 3 lần không thành công tới đối tác này. Vui lòng đợi 2 phút trước khi thử lại.",
                ErrorCodes.CALL_COOLDOWN_ACTIVE
            )
        }
    }

    fun orderUserIds(firstId: String, secondId: String): Pair<String, String> {
        return if (firstId < secondId) firstId to secondId else secondId to firstId
    }

    fun validateReceiverJoin(currentUserId: String, expectedReceiverId: String, currentStatus: String) {
        if (currentUserId != expectedReceiverId) {
            throw CallForbiddenException("Chỉ người nhận mới có quyền tham gia cuộc gọi", ErrorCodes.FORBIDDEN)
        }
        if (currentStatus in CallStatus.terminalStatuses()) {
            throw CallConflictException("Cuộc gọi đã kết thúc", ErrorCodes.CONFLICT)
        }
    }

    fun validateAcceptConnected(userId: String, expectedReceiverId: String, currentStatus: String): Boolean {
        if (userId != expectedReceiverId) {
            throw CallForbiddenException("Chỉ người nhận mới có quyền xác nhận kết nối cuộc gọi", ErrorCodes.FORBIDDEN)
        }
        if (currentStatus == CallStatus.CONNECTED.name) {
            return false // Already connected, no update needed (idempotent)
        }
        if (currentStatus in CallStatus.terminalStatuses()) {
            throw CallConflictException("Cuộc gọi đã kết thúc, không thể kết nối", ErrorCodes.CONFLICT)
        }
        return true
    }

    fun validateRejectCall(userId: String, expectedReceiverId: String, currentStatus: String): Boolean {
        if (userId != expectedReceiverId) {
            throw CallForbiddenException("Chỉ người nhận mới có quyền từ chối cuộc gọi", ErrorCodes.FORBIDDEN)
        }
        if (currentStatus in CallStatus.terminalStatuses()) {
            return false // Already terminal, no update needed (idempotent)
        }
        return true
    }

    fun validateEndCallParticipant(userId: String, callerId: String, receiverId: String) {
        if (userId != callerId && userId != receiverId) {
            throw CallForbiddenException("Bạn không phải thành viên của cuộc gọi này", ErrorCodes.NOT_PARTICIPANT)
        }
    }

    fun calculateCallDuration(currentStatus: String, connectedTime: LocalDateTime?, now: LocalDateTime): Int {
        return if (currentStatus == CallStatus.CONNECTED.name && connectedTime != null) {
            maxOf(0, Duration.between(connectedTime, now).seconds.toInt())
        } else {
            0
        }
    }

    fun determineEndStatus(wasUnconnected: Boolean): String {
        return if (wasUnconnected) CallStatus.MISSED.name else CallStatus.ENDED.name
    }

    fun checkRateLimit(
        userId: String,
        timestampsMap: ConcurrentHashMap<String, MutableList<Long>>,
        lastCallInitiatedMap: ConcurrentHashMap<String, Long>?,
        now: Long = System.currentTimeMillis(),
        maxRequests: Int = 5,
        windowMs: Long = 60_000L,
        cooldownMs: Long = 10_000L
    ) {
        val timestamps = timestampsMap.computeIfAbsent(userId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > windowMs }
            if (timestamps.size >= maxRequests) {
                throw CallRateLimitException(
                    "Bạn đã yêu cầu tạo cuộc gọi quá nhiều lần. Vui lòng đợi 1 phút.",
                    ErrorCodes.RATE_LIMITED
                )
            }
            timestamps.add(now)
        }

        if (lastCallInitiatedMap != null) {
            val lastTime = lastCallInitiatedMap[userId]
            if (lastTime != null && now - lastTime < cooldownMs) {
                val remainSeconds = ((cooldownMs - (now - lastTime)) / 1000L) + 1
                throw CallRateLimitException(
                    "Vui lòng đợi $remainSeconds giây trước khi bấm gọi lại.",
                    ErrorCodes.CALL_COOLDOWN_ACTIVE
                )
            }
        }
    }

    fun checkActionRateLimit(
        userId: String,
        timestampsMap: ConcurrentHashMap<String, MutableList<Long>>,
        now: Long = System.currentTimeMillis(),
        maxRequests: Int = 30,
        windowMs: Long = 60_000L
    ) {
        val timestamps = timestampsMap.computeIfAbsent(userId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > windowMs }
            if (timestamps.size >= maxRequests) {
                throw CallRateLimitException(
                    "Quá nhiều yêu cầu thao tác cuộc gọi. Vui lòng đợi 1 phút.",
                    ErrorCodes.RATE_LIMITED
                )
            }
            timestamps.add(now)
        }
    }
}

