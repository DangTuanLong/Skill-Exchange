package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.exchange.RescheduleStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

open class RescheduleForbiddenException(message: String, val errorCode: String = ErrorCodes.FORBIDDEN) : RuntimeException(message)
open class RescheduleConflictException(message: String, val errorCode: String = ErrorCodes.CONFLICT) : RuntimeException(message)
open class RescheduleNotFoundException(message: String, val errorCode: String = ErrorCodes.NOT_FOUND) : RuntimeException(message)
open class RescheduleValidationException(
    message: String,
    val errorCode: String = ErrorCodes.VALIDATION_FAILED,
    val fieldErrors: Map<String, String>? = null
) : RuntimeException(message)

/**
 * Pure logic helper for reschedule proposals, validations, state transitions, and formatting.
 */
object RescheduleLogicHelper {

    private val UTC_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")

    fun parseExchangeUuid(exchangeId: String): UUID {
        return try {
            UUID.fromString(exchangeId)
        } catch (_: Exception) {
            throw RescheduleNotFoundException("Không tìm thấy yêu cầu trao đổi")
        }
    }

    fun parseProposalUuid(proposalId: String): UUID {
        return try {
            UUID.fromString(proposalId)
        } catch (_: Exception) {
            throw RescheduleNotFoundException("Không tìm thấy đề xuất đổi lịch")
        }
    }

    fun validateParticipant(userId: String, senderId: String, receiverId: String) {
        if (userId != senderId && userId != receiverId) {
            throw RescheduleForbiddenException("Bạn không phải thành viên của phiên trao đổi này", ErrorCodes.NOT_PARTICIPANT)
        }
    }

    fun validateExchangeAccepted(status: String) {
        if (status != ExchangeStatus.ACCEPTED.name) {
            throw RescheduleConflictException(
                "Chỉ có thể đề xuất đổi lịch khi yêu cầu trao đổi đang ở trạng thái đã chấp nhận (ACCEPTED).",
                ErrorCodes.EXCHANGE_NOT_ACTIVE
            )
        }
    }

    fun parseIsoDateTime(str: String): LocalDateTime {
        return try {
            if (str.endsWith("Z") || str.contains("+")) {
                Instant.parse(str).atZone(ZoneOffset.UTC).toLocalDateTime()
            } else {
                LocalDateTime.parse(str)
            }
        } catch (_: Exception) {
            throw RescheduleValidationException(
                "Định dạng thời gian đề xuất mới không hợp lệ.",
                ErrorCodes.VALIDATION_FAILED
            )
        }
    }

    fun validateNewScheduledAt(newScheduledAtStr: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val dt = parseIsoDateTime(newScheduledAtStr)
        val minTime = now.plusMinutes(15)
        if (!dt.isAfter(minTime)) {
            throw RescheduleValidationException(
                "Thời gian đề xuất mới phải sau thời điểm hiện tại ít nhất 15 phút.",
                ErrorCodes.INVALID_RESCHEDULE_TIME,
                mapOf("new_scheduled_at" to "Thời gian phải sau hiện tại ít nhất 15 phút")
            )
        }
        return dt
    }

    fun validateReason(reason: String?): String? {
        val trimmed = reason?.trim().takeUnless { it.isNullOrBlank() }
        if (trimmed != null && trimmed.length > 200) {
            throw RescheduleValidationException(
                "Lý do đổi lịch không được vượt quá 200 ký tự.",
                ErrorCodes.VALIDATION_FAILED,
                mapOf("reason" to "Lý do không được vượt quá 200 ký tự")
            )
        }
        return trimmed
    }

    fun checkProposalLimit(totalCount: Long) {
        if (totalCount >= 3) {
            throw RescheduleValidationException(
                "Buổi trao đổi này đã sử dụng hết giới hạn 3 lần đề xuất đổi lịch.",
                ErrorCodes.RESCHEDULE_LIMIT_EXCEEDED
            )
        }
    }

    fun isProposalExpired(
        status: String,
        createdAt: LocalDateTime,
        newScheduledAt: LocalDateTime,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (status == RescheduleStatus.PENDING.name) {
            val isTimedOut24h = createdAt.plusHours(24).isBefore(now)
            val isPassedSchedule = !newScheduledAt.isAfter(now)
            return isTimedOut24h || isPassedSchedule
        }
        return false
    }

    fun validateAcceptEligibility(callerId: String, proposerId: String, status: String): Boolean {
        if (callerId == proposerId) {
            throw RescheduleForbiddenException("Bạn không thể chấp nhận đề xuất đổi lịch do chính mình tạo ra.")
        }
        if (status == RescheduleStatus.ACCEPTED.name) {
            return false // Idempotent no-op
        }
        if (status != RescheduleStatus.PENDING.name) {
            throw RescheduleConflictException(
                "Đề xuất đổi lịch không ở trạng thái chờ phản hồi (PENDING).",
                ErrorCodes.PROPOSAL_NOT_PENDING
            )
        }
        return true
    }

    fun validateRejectEligibility(callerId: String, proposerId: String, status: String): Boolean {
        if (callerId == proposerId) {
            throw RescheduleForbiddenException("Bạn không thể từ chối đề xuất đổi lịch do chính mình tạo ra.")
        }
        if (status == RescheduleStatus.REJECTED.name) {
            return false // Idempotent no-op
        }
        if (status != RescheduleStatus.PENDING.name) {
            throw RescheduleConflictException(
                "Đề xuất đổi lịch không ở trạng thái chờ phản hồi (PENDING).",
                ErrorCodes.PROPOSAL_NOT_PENDING
            )
        }
        return true
    }

    fun validateCancelEligibility(callerId: String, proposerId: String, status: String): Boolean {
        if (callerId != proposerId) {
            throw RescheduleForbiddenException("Chỉ người tạo đề xuất mới có quyền hủy đề xuất này.")
        }
        if (status == RescheduleStatus.CANCELLED.name) {
            return false // Idempotent no-op
        }
        if (status != RescheduleStatus.PENDING.name) {
            throw RescheduleConflictException(
                "Đề xuất đổi lịch không ở trạng thái chờ phản hồi (PENDING).",
                ErrorCodes.PROPOSAL_NOT_PENDING
            )
        }
        return true
    }

    fun formatScheduledAtForReminder(scheduledAt: LocalDateTime): String {
        return scheduledAt.format(UTC_FORMATTER)
    }

    private val DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM")

    fun formatDisplayTime(dateTime: LocalDateTime): String {
        return dateTime.format(DISPLAY_TIME_FORMATTER)
    }

    fun formatProposedNotificationBody(callerName: String, scheduledAt: LocalDateTime): String {
        return "$callerName đề xuất đổi lịch hẹn sang ${formatDisplayTime(scheduledAt)}"
    }

    fun formatAcceptedNotificationBody(callerName: String, scheduledAt: LocalDateTime): String {
        return "$callerName đã đồng ý đổi lịch hẹn sang ${formatDisplayTime(scheduledAt)}"
    }

    fun formatRejectedNotificationBody(callerName: String): String {
        return "$callerName đã từ chối đề xuất đổi lịch hẹn"
    }

    fun formatRescheduleDataPayload(exchangeId: String, proposalId: String): String {
        return """{"exchangeId":"$exchangeId","proposalId":"$proposalId"}"""
    }
}

