package com.skillexchange.api.services.rating

import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.rating.CreateRatingRequest
import com.skillexchange.api.plugins.ValidationException
import java.util.UUID

/**
 * Pure logic helper for rating validations, member checks, and reviewee determination.
 */
object RatingLogicHelper {

    fun validateCreateRatingInput(req: CreateRatingRequest): Pair<Int, String?> {
        if (req.score !in 1..5) {
            throw ValidationException("Điểm đánh giá phải từ 1 đến 5", mapOf("score" to "Điểm đánh giá phải từ 1 đến 5"))
        }

        val trimmedComment = req.comment?.trim().takeUnless { it.isNullOrBlank() }
        if (trimmedComment != null && trimmedComment.length > 200) {
            throw ValidationException("Nhận xét không được vượt quá 200 ký tự", mapOf("comment" to "Nhận xét không được vượt quá 200 ký tự"))
        }

        return Pair(req.score, trimmedComment)
    }

    fun parseExchangeUuid(exchangeId: String): UUID {
        return try {
            UUID.fromString(exchangeId)
        } catch (_: Exception) {
            throw RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")
        }
    }

    fun validateRatingEligibility(
        callerUserId: String,
        senderId: String,
        receiverId: String,
        currentStatus: String,
        hasExistingRating: Boolean
    ): String {
        validateExchangeMember(callerUserId, senderId, receiverId)

        if (currentStatus != ExchangeStatus.COMPLETED.name) {
            throw ValidationException("Chỉ có thể đánh giá phiên trao đổi đã hoàn thành (COMPLETED)")
        }

        if (hasExistingRating) {
            throw RatingConflictException("Bạn đã đánh giá phiên trao đổi này rồi")
        }

        return if (callerUserId == senderId) receiverId else senderId
    }

    fun validateExchangeMember(callerUserId: String, senderId: String, receiverId: String) {
        if (callerUserId != senderId && callerUserId != receiverId) {
            throw RatingForbiddenException("Bạn không phải thành viên của phiên trao đổi này")
        }
    }
}

