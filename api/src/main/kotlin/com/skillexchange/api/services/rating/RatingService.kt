package com.skillexchange.api.services.rating

import com.skillexchange.api.models.db.ExchangeRequestsTable
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.RatingsTable
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.models.rating.CreateRatingRequest
import com.skillexchange.api.models.rating.RatingDto
import com.skillexchange.api.models.rating.ReputationDto
import com.skillexchange.api.models.rating.UserExchangeRatingStatusDto
import com.skillexchange.api.plugins.ValidationException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class RatingNotFoundException(message: String) : RuntimeException(message)
class RatingForbiddenException(message: String) : RuntimeException(message)
class RatingConflictException(message: String) : RuntimeException(message)

open class RatingService {

    /**
     * Tạo đánh giá mới sau khi phiên trao đổi đã COMPLETED.
     */
    open fun createRating(callerUserId: String, req: CreateRatingRequest): RatingDto = transaction {
        if (req.score !in 1..5) {
            throw ValidationException("Điểm đánh giá phải từ 1 đến 5", mapOf("score" to "Điểm đánh giá phải từ 1 đến 5"))
        }

        val trimmedComment = req.comment?.trim().takeUnless { it.isNullOrBlank() }
        if (trimmedComment != null && trimmedComment.length > 200) {
            throw ValidationException("Nhận xét không được vượt quá 200 ký tự", mapOf("comment" to "Nhận xét không được vượt quá 200 ký tự"))
        }

        val exchangeUuid = try {
            UUID.fromString(req.exchangeId)
        } catch (e: Exception) {
            throw RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")
        }

        val exchangeRow = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.id eq exchangeUuid }
            .firstOrNull() ?: throw RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")

        val senderId = exchangeRow[ExchangeRequestsTable.senderId]
        val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]

        if (callerUserId != senderId && callerUserId != receiverId) {
            throw RatingForbiddenException("Bạn không phải thành viên của phiên trao đổi này")
        }

        val currentStatus = exchangeRow[ExchangeRequestsTable.status]
        if (currentStatus != ExchangeStatus.COMPLETED.name) {
            throw ValidationException("Chỉ có thể đánh giá phiên trao đổi đã hoàn thành (COMPLETED)")
        }

        val existing = RatingsTable.selectAll()
            .where { (RatingsTable.exchangeId eq exchangeUuid) and (RatingsTable.reviewerId eq callerUserId) }
            .firstOrNull()
        if (existing != null) {
            throw RatingConflictException("Bạn đã đánh giá phiên trao đổi này rồi")
        }

        val revieweeId = if (callerUserId == senderId) receiverId else senderId
        val ratingId = UUID.randomUUID()
        val now = LocalDateTime.now()

        RatingsTable.insert {
            it[id] = ratingId
            it[exchangeId] = exchangeUuid
            it[reviewerId] = callerUserId
            it[this.revieweeId] = revieweeId
            it[score] = req.score
            it[comment] = trimmedComment
            it[createdAt] = now
        }

        val reviewerProfile = ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq callerUserId }
            .firstOrNull()

        RatingDto(
            id = ratingId.toString(),
            exchangeId = req.exchangeId,
            reviewerId = callerUserId,
            reviewerName = reviewerProfile?.get(ProfilesTable.fullName),
            reviewerAvatarUrl = reviewerProfile?.get(ProfilesTable.avatarUrl),
            revieweeId = revieweeId,
            score = req.score,
            comment = trimmedComment,
            createdAt = now.toString()
        )
    }

    /**
     * Lấy danh sách đánh giá của một người dùng (revieweeId = userId).
     */
    open fun getUserRatings(userId: String): List<RatingDto> = transaction {
        val rows = RatingsTable.selectAll()
            .where { RatingsTable.revieweeId eq userId }
            .orderBy(RatingsTable.createdAt to SortOrder.DESC)
            .toList()

        if (rows.isEmpty()) return@transaction emptyList()

        val reviewerIds = rows.map { it[RatingsTable.reviewerId] }.distinct()
        val profilesMap = ProfilesTable.selectAll()
            .where { ProfilesTable.userId inList reviewerIds }
            .associateBy { it[ProfilesTable.userId] }

        rows.map { r ->
            val reviewerId = r[RatingsTable.reviewerId]
            val profile = profilesMap[reviewerId]
            RatingDto(
                id = r[RatingsTable.id].toString(),
                exchangeId = r[RatingsTable.exchangeId].toString(),
                reviewerId = reviewerId,
                reviewerName = profile?.get(ProfilesTable.fullName),
                reviewerAvatarUrl = profile?.get(ProfilesTable.avatarUrl),
                revieweeId = r[RatingsTable.revieweeId],
                score = r[RatingsTable.score],
                comment = r[RatingsTable.comment],
                createdAt = r[RatingsTable.createdAt].toString()
            )
        }
    }

    /**
     * Tính toán danh tiếng (Reputation) khi đọc theo pure function ReputationCalculator.
     */
    open fun getUserReputation(userId: String): ReputationDto = transaction {
        val ratingScores = RatingsTable.selectAll()
            .where { RatingsTable.revieweeId eq userId }
            .map { it[RatingsTable.score] }

        val completedCount = ExchangeRequestsTable.selectAll()
            .where {
                ((ExchangeRequestsTable.senderId eq userId) or (ExchangeRequestsTable.receiverId eq userId)) and
                        (ExchangeRequestsTable.status eq ExchangeStatus.COMPLETED.name)
            }
            .count().toInt()

        val cancelledAfterAcceptedCount = ExchangeRequestsTable.selectAll()
            .where {
                ((ExchangeRequestsTable.senderId eq userId) or (ExchangeRequestsTable.receiverId eq userId)) and
                        (ExchangeRequestsTable.status eq ExchangeStatus.CANCELLED.name) and
                        ExchangeRequestsTable.acceptedAt.isNotNull()
            }
            .count().toInt()

        val result = ReputationCalculator.calculate(
            ratings = ratingScores,
            completedCount = completedCount,
            cancelledAfterAcceptedCount = cancelledAfterAcceptedCount
        )

        ReputationDto(
            score = result.score,
            badge = result.badge,
            totalExchanges = result.totalExchanges,
            completionRate = result.completionRate,
            ratingCount = result.ratingCount,
            avgRating = result.avgRating
        )
    }

    /**
     * Kiểm tra trạng thái đánh giá của caller đối với một exchange cụ thể.
     */
    open fun getMyExchangeRating(callerUserId: String, exchangeId: String): UserExchangeRatingStatusDto = transaction {
        val exchangeUuid = try {
            UUID.fromString(exchangeId)
        } catch (e: Exception) {
            throw RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")
        }

        val exchangeRow = ExchangeRequestsTable.selectAll()
            .where { ExchangeRequestsTable.id eq exchangeUuid }
            .firstOrNull() ?: throw RatingNotFoundException("Không tìm thấy yêu cầu trao đổi")

        val senderId = exchangeRow[ExchangeRequestsTable.senderId]
        val receiverId = exchangeRow[ExchangeRequestsTable.receiverId]

        if (callerUserId != senderId && callerUserId != receiverId) {
            throw RatingForbiddenException("Bạn không phải thành viên của phiên trao đổi này")
        }

        val ratingRow = RatingsTable.selectAll()
            .where { (RatingsTable.exchangeId eq exchangeUuid) and (RatingsTable.reviewerId eq callerUserId) }
            .firstOrNull()

        if (ratingRow == null) {
            UserExchangeRatingStatusDto(hasRated = false, rating = null)
        } else {
            val reviewerProfile = ProfilesTable.selectAll()
                .where { ProfilesTable.userId eq callerUserId }
                .firstOrNull()

            val dto = RatingDto(
                id = ratingRow[RatingsTable.id].toString(),
                exchangeId = exchangeId,
                reviewerId = callerUserId,
                reviewerName = reviewerProfile?.get(ProfilesTable.fullName),
                reviewerAvatarUrl = reviewerProfile?.get(ProfilesTable.avatarUrl),
                revieweeId = ratingRow[RatingsTable.revieweeId],
                score = ratingRow[RatingsTable.score],
                comment = ratingRow[RatingsTable.comment],
                createdAt = ratingRow[RatingsTable.createdAt].toString()
            )
            UserExchangeRatingStatusDto(hasRated = true, rating = dto)
        }
    }
}
