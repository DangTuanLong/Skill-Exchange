package com.skillexchange.api.services.rating

import kotlin.math.min
import kotlin.math.round

object ReputationCalculator {

    const val BADGE_NEW_MEMBER = "Thành viên mới"
    const val BADGE_TRUSTED = "Đáng tin cậy"
    const val BADGE_EXCELLENT = "Xuất sắc"

    data class Result(
        val score: Double?,
        val badge: String?,
        val totalExchanges: Int,
        val completionRate: Double,
        val ratingCount: Int,
        val avgRating: Double?
    )

    /**
     * Hàm thuần tính điểm danh tiếng và huy hiệu (Reputation & Badge).
     *
     * Công thức (BUSINESS_RULES.md §4):
     * Reputation = AVG(ratings) * 0.6
     *            + (CompletionRate * 5) * 0.3
     *            + (ActivityBonus * 5) * 0.1
     *
     * CompletionRate = Completed / (Completed + CancelledAfterAccepted)
     * - Mẫu số = 0 -> CompletionRate = 0.0
     * ActivityBonus  = min(1.0, total_exchanges / 20.0)
     * total_exchanges = số phiên COMPLETED mà người dùng tham gia (sessions).
     *
     * Quy tắc huy hiệu:
     * - Chưa có đánh giá nào (ratingCount == 0): badge = "Thành viên mới", score = null (không hiển thị 0.0★).
     * - ⚪ Thành viên mới: score < 2.0 HOẶC sessions < 3
     * - 🟡 Xuất sắc: score >= 4.5 VÀ sessions >= 15 (ưu tiên cao nhất khi cả hai thỏa mãn)
     * - 🟢 Đáng tin cậy: score >= 3.5 VÀ sessions >= 5
     * - Vùng giữa (gaps, ví dụ score 2.0-3.5 với >= 3 sessions, hoặc score >= 3.5 với 3-4 sessions): null (không có huy hiệu)
     */
    fun calculate(
        ratings: List<Int>,
        completedCount: Int,
        cancelledAfterAcceptedCount: Int
    ): Result {
        val totalExchanges = completedCount
        val totalAttempts = completedCount + cancelledAfterAcceptedCount
        val rawCompletionRate = if (totalAttempts == 0) 0.0 else completedCount.toDouble() / totalAttempts
        val completionRate = round(rawCompletionRate * 100.0) / 100.0

        if (ratings.isEmpty()) {
            return Result(
                score = null,
                badge = BADGE_NEW_MEMBER,
                totalExchanges = totalExchanges,
                completionRate = completionRate,
                ratingCount = 0,
                avgRating = null
            )
        }

        val rawAvgRating = ratings.average()
        val avgRating = round(rawAvgRating * 10.0) / 10.0

        val activityBonus = min(1.0, completedCount / 20.0)

        val rawScore = avgRating * 0.6 + (rawCompletionRate * 5.0) * 0.3 + (activityBonus * 5.0) * 0.1
        val score = round(rawScore * 10.0) / 10.0

        val sessions = completedCount
        val badge = when {
            score < 2.0 || sessions < 3 -> BADGE_NEW_MEMBER
            score >= 4.5 && sessions >= 15 -> BADGE_EXCELLENT
            score >= 3.5 && sessions >= 5 -> BADGE_TRUSTED
            else -> null
        }

        return Result(
            score = score,
            badge = badge,
            totalExchanges = totalExchanges,
            completionRate = completionRate,
            ratingCount = ratings.size,
            avgRating = avgRating
        )
    }
}
