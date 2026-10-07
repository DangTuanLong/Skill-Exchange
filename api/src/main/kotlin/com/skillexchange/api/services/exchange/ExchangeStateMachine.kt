package com.skillexchange.api.services.exchange

import com.skillexchange.api.models.exchange.ExchangeStatus
import java.time.LocalDateTime

class ExchangeForbiddenException(message: String) : RuntimeException(message)
class ExchangeConflictException(message: String) : RuntimeException(message)

data class TransitionResult(
    val newStatus: ExchangeStatus,
    val senderCompletedAt: LocalDateTime?,
    val receiverCompletedAt: LocalDateTime?,
    val cancellationReason: String?,
    val isChanged: Boolean,
    val acceptedAt: LocalDateTime? = null
)

object ExchangeStateMachine {

    /**
     * Chấp nhận yêu cầu: PENDING -> ACCEPTED (chỉ Receiver).
     * Idempotent nếu đã ở ACCEPTED.
     */
    fun accept(
        currentStatus: ExchangeStatus,
        senderId: String,
        receiverId: String,
        callerUserId: String,
        senderCompletedAt: LocalDateTime?,
        receiverCompletedAt: LocalDateTime?,
        cancellationReason: String?,
        currentAcceptedAt: LocalDateTime? = null,
        now: LocalDateTime = LocalDateTime.now()
    ): TransitionResult {
        validateParticipant(callerUserId, senderId, receiverId)

        if (callerUserId != receiverId) {
            throw ExchangeForbiddenException("Chỉ người nhận mới có quyền chấp nhận yêu cầu")
        }

        if (currentStatus == ExchangeStatus.ACCEPTED) {
            return TransitionResult(
                newStatus = ExchangeStatus.ACCEPTED,
                senderCompletedAt = senderCompletedAt,
                receiverCompletedAt = receiverCompletedAt,
                cancellationReason = cancellationReason,
                isChanged = false,
                acceptedAt = currentAcceptedAt
            )
        }

        if (currentStatus != ExchangeStatus.PENDING) {
            throw ExchangeConflictException("Không thể chấp nhận yêu cầu ở trạng thái $currentStatus")
        }

        return TransitionResult(
            newStatus = ExchangeStatus.ACCEPTED,
            senderCompletedAt = senderCompletedAt,
            receiverCompletedAt = receiverCompletedAt,
            cancellationReason = cancellationReason,
            isChanged = true,
            acceptedAt = currentAcceptedAt ?: now
        )
    }

    /**
     * Từ chối yêu cầu: PENDING -> REJECTED (chỉ Receiver).
     * Idempotent nếu đã ở REJECTED.
     */
    fun reject(
        currentStatus: ExchangeStatus,
        senderId: String,
        receiverId: String,
        callerUserId: String,
        senderCompletedAt: LocalDateTime?,
        receiverCompletedAt: LocalDateTime?,
        cancellationReason: String?
    ): TransitionResult {
        validateParticipant(callerUserId, senderId, receiverId)

        if (callerUserId != receiverId) {
            throw ExchangeForbiddenException("Chỉ người nhận mới có quyền từ chối yêu cầu")
        }

        if (currentStatus == ExchangeStatus.REJECTED) {
            return TransitionResult(
                newStatus = ExchangeStatus.REJECTED,
                senderCompletedAt = senderCompletedAt,
                receiverCompletedAt = receiverCompletedAt,
                cancellationReason = cancellationReason,
                isChanged = false
            )
        }

        if (currentStatus != ExchangeStatus.PENDING) {
            throw ExchangeConflictException("Không thể từ chối yêu cầu ở trạng thái $currentStatus")
        }

        return TransitionResult(
            newStatus = ExchangeStatus.REJECTED,
            senderCompletedAt = senderCompletedAt,
            receiverCompletedAt = receiverCompletedAt,
            cancellationReason = cancellationReason,
            isChanged = true
        )
    }

    /**
     * Hủy yêu cầu:
     * - PENDING -> CANCELLED: chỉ Sender được hủy.
     * - ACCEPTED -> CANCELLED: cả Sender và Receiver đều được hủy (kèm lý do tùy chọn).
     * Idempotent nếu đã ở CANCELLED.
     */
    fun cancel(
        currentStatus: ExchangeStatus,
        senderId: String,
        receiverId: String,
        callerUserId: String,
        reason: String?,
        senderCompletedAt: LocalDateTime?,
        receiverCompletedAt: LocalDateTime?,
        existingReason: String?,
        currentAcceptedAt: LocalDateTime? = null
    ): TransitionResult {
        validateParticipant(callerUserId, senderId, receiverId)

        if (currentStatus == ExchangeStatus.CANCELLED) {
            return TransitionResult(
                newStatus = ExchangeStatus.CANCELLED,
                senderCompletedAt = senderCompletedAt,
                receiverCompletedAt = receiverCompletedAt,
                cancellationReason = existingReason,
                isChanged = false,
                acceptedAt = currentAcceptedAt
            )
        }

        when (currentStatus) {
            ExchangeStatus.PENDING -> {
                if (callerUserId != senderId) {
                    throw ExchangeForbiddenException("Chỉ người gửi mới có quyền hủy yêu cầu đang chờ duyệt. Người nhận vui lòng chọn từ chối.")
                }
                return TransitionResult(
                    newStatus = ExchangeStatus.CANCELLED,
                    senderCompletedAt = senderCompletedAt,
                    receiverCompletedAt = receiverCompletedAt,
                    cancellationReason = reason?.trim().takeUnless { it.isNullOrBlank() } ?: existingReason,
                    isChanged = true,
                    acceptedAt = currentAcceptedAt
                )
            }
            ExchangeStatus.ACCEPTED -> {
                // Cả hai bên đều có thể hủy khi đã ACCEPTED
                return TransitionResult(
                    newStatus = ExchangeStatus.CANCELLED,
                    senderCompletedAt = senderCompletedAt,
                    receiverCompletedAt = receiverCompletedAt,
                    cancellationReason = reason?.trim().takeUnless { it.isNullOrBlank() } ?: existingReason,
                    isChanged = true,
                    acceptedAt = currentAcceptedAt
                )
            }
            else -> {
                throw ExchangeConflictException("Không thể hủy yêu cầu ở trạng thái $currentStatus")
            }
        }
    }

    /**
     * Xác nhận hoàn thành: ACCEPTED -> COMPLETED khi cả hai đã xác nhận.
     * Khi 1 bên xác nhận: lưu timestamp và giữ trạng thái ACCEPTED.
     * Khi cả 2 bên xác nhận: chuyển sang COMPLETED.
     * Idempotent nếu người đó đã xác nhận trước đó hoặc đã COMPLETED.
     */
    fun complete(
        currentStatus: ExchangeStatus,
        senderId: String,
        receiverId: String,
        callerUserId: String,
        now: LocalDateTime,
        senderCompletedAt: LocalDateTime?,
        receiverCompletedAt: LocalDateTime?,
        cancellationReason: String?,
        currentAcceptedAt: LocalDateTime? = null
    ): TransitionResult {
        validateParticipant(callerUserId, senderId, receiverId)

        if (currentStatus == ExchangeStatus.COMPLETED) {
            return TransitionResult(
                newStatus = ExchangeStatus.COMPLETED,
                senderCompletedAt = senderCompletedAt,
                receiverCompletedAt = receiverCompletedAt,
                cancellationReason = cancellationReason,
                isChanged = false,
                acceptedAt = currentAcceptedAt
            )
        }

        if (currentStatus != ExchangeStatus.ACCEPTED) {
            throw ExchangeConflictException("Chỉ có thể xác nhận hoàn thành cho yêu cầu đã được chấp nhận (trạng thái hiện tại: $currentStatus)")
        }

        var newSenderCompleted = senderCompletedAt
        var newReceiverCompleted = receiverCompletedAt
        var changed = false

        if (callerUserId == senderId) {
            if (senderCompletedAt == null) {
                newSenderCompleted = now
                changed = true
            }
        } else if (callerUserId == receiverId) {
            if (receiverCompletedAt == null) {
                newReceiverCompleted = now
                changed = true
            }
        }

        val bothConfirmed = newSenderCompleted != null && newReceiverCompleted != null
        val newStatus = if (bothConfirmed) ExchangeStatus.COMPLETED else ExchangeStatus.ACCEPTED

        return TransitionResult(
            newStatus = newStatus,
            senderCompletedAt = newSenderCompleted,
            receiverCompletedAt = newReceiverCompleted,
            cancellationReason = cancellationReason,
            isChanged = changed || (newStatus != currentStatus),
            acceptedAt = currentAcceptedAt
        )
    }

    private fun validateParticipant(callerUserId: String, senderId: String, receiverId: String) {
        if (callerUserId != senderId && callerUserId != receiverId) {
            throw ExchangeForbiddenException("Bạn không phải thành viên tham gia yêu cầu trao đổi này")
        }
    }
}
