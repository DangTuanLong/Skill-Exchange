package com.skillexchange.api.services.stats

import com.skillexchange.api.models.db.*
import com.skillexchange.api.models.stats.*
import com.skillexchange.api.services.rating.ReputationCalculator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class StatsService {

    /**
     * Lấy thống kê cá nhân tính động khi đọc.
     */
    fun getUserStats(userId: String, now: LocalDateTime = LocalDateTime.now()): UserStatsDto = transaction {
        // 1. Số phiên hoàn thành (totalSessions)
        val completedExchanges = ExchangeRequestsTable.selectAll()
            .where {
                ((ExchangeRequestsTable.senderId eq userId) or (ExchangeRequestsTable.receiverId eq userId)) and
                        (ExchangeRequestsTable.status eq "COMPLETED")
            }
            .toList()

        val totalSessions = completedExchanges.size

        // 2. Rating & Reputation stats
        val ratingScores = RatingsTable.selectAll()
            .where { RatingsTable.revieweeId eq userId }
            .map { it[RatingsTable.score] }

        val avgRating = if (ratingScores.isNotEmpty()) {
            Math.round(ratingScores.average() * 10.0) / 10.0
        } else {
            0.0
        }
        val ratingCount = ratingScores.size

        val cancelledAfterAcceptedCount = ExchangeRequestsTable.selectAll()
            .where {
                ((ExchangeRequestsTable.senderId eq userId) or (ExchangeRequestsTable.receiverId eq userId)) and
                        (ExchangeRequestsTable.status eq "CANCELLED") and
                        ExchangeRequestsTable.acceptedAt.isNotNull()
            }
            .count().toInt()

        val completionRate = if (totalSessions + cancelledAfterAcceptedCount > 0) {
            val rate = totalSessions.toDouble() / (totalSessions + cancelledAfterAcceptedCount)
            Math.round(rate * 100.0) / 100.0
        } else {
            0.0
        }

        // 3. Phiên theo 6 tháng gần nhất (bao gồm cả tháng hiện tại)
        val completedTimestamps = completedExchanges.map { row ->
            row[ExchangeRequestsTable.senderCompletedAt]
                ?: row[ExchangeRequestsTable.receiverCompletedAt]
                ?: row[ExchangeRequestsTable.updatedAt]
        }
        val monthlySessions = StatsCalculator.calculateMonthlySessions(completedTimestamps, now)

        // 4. Timeline (tối đa 10 hoạt động gần nhất)
        val timelineItems = mutableListOf<TimelineItemDto>()

        // Lấy thông tin partner cho các completed exchanges
        val partnerIds = completedExchanges.map { row ->
            val sId = row[ExchangeRequestsTable.senderId]
            val rId = row[ExchangeRequestsTable.receiverId]
            if (sId == userId) rId else sId
        }.distinct()

        val partnerProfiles = if (partnerIds.isNotEmpty()) {
            ProfilesTable.selectAll()
                .where { ProfilesTable.userId inList partnerIds }
                .associate { it[ProfilesTable.userId] to it[ProfilesTable.fullName] }
        } else {
            emptyMap()
        }

        completedExchanges.forEach { row ->
            val exId = row[ExchangeRequestsTable.id].toString()
            val sId = row[ExchangeRequestsTable.senderId]
            val rId = row[ExchangeRequestsTable.receiverId]
            val partnerId = if (sId == userId) rId else sId
            val partnerName = partnerProfiles[partnerId] ?: "Thành viên"
            val time = row[ExchangeRequestsTable.senderCompletedAt]
                ?: row[ExchangeRequestsTable.receiverCompletedAt]
                ?: row[ExchangeRequestsTable.updatedAt]

            timelineItems.add(
                TimelineItemDto(
                    id = "ex_$exId",
                    type = "EXCHANGE",
                    title = "Hoàn thành buổi trao đổi",
                    description = "Bạn đã hoàn thành phiên trao đổi kỹ năng với $partnerName.",
                    timestamp = time.toString()
                )
            )
        }

        // Ratings nhận được
        val ratingsReceived = RatingsTable.selectAll()
            .where { RatingsTable.revieweeId eq userId }
            .toList()

        val reviewerIds = ratingsReceived.map { it[RatingsTable.reviewerId] }.distinct()
        val reviewerProfiles = if (reviewerIds.isNotEmpty()) {
            ProfilesTable.selectAll()
                .where { ProfilesTable.userId inList reviewerIds }
                .associate { it[ProfilesTable.userId] to it[ProfilesTable.fullName] }
        } else {
            emptyMap()
        }

        ratingsReceived.forEach { row ->
            val rId = row[RatingsTable.id].toString()
            val reviewerId = row[RatingsTable.reviewerId]
            val reviewerName = reviewerProfiles[reviewerId] ?: "Đối tác"
            val score = row[RatingsTable.score]
            val comment = row[RatingsTable.comment]
            val time = row[RatingsTable.createdAt]

            timelineItems.add(
                TimelineItemDto(
                    id = "rt_$rId",
                    type = "RATING",
                    title = "Nhận đánh giá mới ($score★)",
                    description = if (!comment.isNullOrBlank()) "\"$comment\" — từ $reviewerName" else "Được $reviewerName chấm $score sao.",
                    timestamp = time.toString()
                )
            )
        }

        // Sắp xếp giảm dần theo thời gian và lấy 10 mục
        val sortedTimeline = timelineItems.sortedByDescending { it.timestamp }.take(10)

        // 5. Radar 6 trục kỹ năng (dựa trên 6 nhóm kỹ năng chính)
        val defaultAxes = listOf(
            1 to "Lập trình",
            2 to "Ngoại ngữ",
            3 to "Âm nhạc",
            4 to "Thiết kế",
            5 to "Nấu ăn",
            6 to "Thể thao"
        )

        val userHaveSkills = (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
            .selectAll()
            .where { (UserSkillsTable.userId eq userId) and (UserSkillsTable.type eq "HAVE") }
            .toList()

        val radarScores = defaultAxes.map { (catId, catName) ->
            val skillsInCat = userHaveSkills.filter { it[SkillsTable.categoryId] == catId }
            val maxProficiency = skillsInCat.maxOfOrNull { it[UserSkillsTable.proficiencyLevel] } ?: 0
            RadarAxisDto(
                axisId = catId,
                categoryName = catName,
                score = maxProficiency.toDouble(),
                skillCount = skillsInCat.size
            )
        }

        // 6. Gợi ý khoảng trống kỹ năng (Skill Gap Suggestions dựa trên dữ liệu matching thực tế)
        val myHaveSkillIds = userHaveSkills.map { it[SkillsTable.id] }.toSet()
        val myWantSkillIds = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { (UserSkillsTable.userId eq userId) and (UserSkillsTable.type eq "WANT") }
            .map { it[SkillsTable.id] }
            .toSet()

        val myAllSkillIds = myHaveSkillIds + myWantSkillIds

        val skillGapSuggestions = if (myHaveSkillIds.isNotEmpty()) {
            // Tìm các user B khác có WANT chứa một kỹ năng trong myHaveSkillIds
            val candidatePartners = (UserSkillsTable innerJoin SkillsTable)
                .selectAll()
                .where {
                    (UserSkillsTable.userId neq userId) and
                            (UserSkillsTable.type eq "WANT") and
                            (UserSkillsTable.skillId inList myHaveSkillIds)
                }
                .map { it[UserSkillsTable.userId] }
                .distinct()

            if (candidatePartners.isNotEmpty()) {
                // Lấy tất cả kỹ năng HAVE của các candidatePartners mà user A chưa có
                val partnerHaveSkills = (UserSkillsTable innerJoin SkillsTable innerJoin SkillCategoriesTable)
                    .selectAll()
                    .where {
                        (UserSkillsTable.userId inList candidatePartners) and
                                (UserSkillsTable.type eq "HAVE") and
                                (SkillsTable.id notInList myAllSkillIds)
                    }
                    .toList()

                // Nhóm theo skillId và đếm số lượng candidatePartner distinct
                val skillToPartners = partnerHaveSkills.groupBy { it[SkillsTable.id] }
                skillToPartners.map { (skillId, rows) ->
                    val distinctUsersCount = rows.map { it[UserSkillsTable.userId] }.distinct().size
                    val firstRow = rows.first()
                    val skillName = firstRow[SkillsTable.name]
                    val categoryName = firstRow[SkillCategoriesTable.name]
                    SkillGapSuggestionDto(
                        skillId = skillId,
                        skillName = skillName,
                        categoryName = categoryName,
                        unlockedMatchesCount = distinctUsersCount,
                        reason = "Có $distinctUsersCount thành viên đang cần học kỹ năng của bạn và có thể dạy kỹ năng này."
                    )
                }
                    .sortedByDescending { it.unlockedMatchesCount }
                    .take(3)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        UserStatsDto(
            totalSessions = totalSessions,
            avgRating = avgRating,
            ratingCount = ratingCount,
            completionRate = completionRate,
            monthlySessions = monthlySessions,
            timeline = sortedTimeline,
            radarScores = radarScores,
            skillGapSuggestions = skillGapSuggestions
        )
    }

    /**
     * Bảng xếp hạng Top 10 người dùng theo điểm uy tín (reputation score) tính động khi đọc.
     */
    fun getLeaderboard(): List<LeaderboardItemDto> = transaction {
        val allProfiles = ProfilesTable.selectAll().toList()
        if (allProfiles.isEmpty()) return@transaction emptyList()

        val allUserIds = allProfiles.map { it[ProfilesTable.userId] }

        // Gom ratings theo revieweeId
        val ratingsByUser = RatingsTable.selectAll()
            .where { RatingsTable.revieweeId inList allUserIds }
            .groupBy({ it[RatingsTable.revieweeId] }, { it[RatingsTable.score] })

        // Gom completed exchanges theo user
        val completedExchanges = ExchangeRequestsTable.selectAll()
            .where {
                (ExchangeRequestsTable.status eq "COMPLETED") and
                        ((ExchangeRequestsTable.senderId inList allUserIds) or (ExchangeRequestsTable.receiverId inList allUserIds))
            }
            .toList()

        val completedCountByUser = mutableMapOf<String, Int>()
        completedExchanges.forEach { row ->
            val sId = row[ExchangeRequestsTable.senderId]
            val rId = row[ExchangeRequestsTable.receiverId]
            completedCountByUser[sId] = (completedCountByUser[sId] ?: 0) + 1
            completedCountByUser[rId] = (completedCountByUser[rId] ?: 0) + 1
        }

        // Gom cancelled after accepted
        val cancelledExchanges = ExchangeRequestsTable.selectAll()
            .where {
                (ExchangeRequestsTable.status eq "CANCELLED") and
                        ExchangeRequestsTable.acceptedAt.isNotNull() and
                        ((ExchangeRequestsTable.senderId inList allUserIds) or (ExchangeRequestsTable.receiverId inList allUserIds))
            }
            .toList()

        val cancelledCountByUser = mutableMapOf<String, Int>()
        cancelledExchanges.forEach { row ->
            val sId = row[ExchangeRequestsTable.senderId]
            val rId = row[ExchangeRequestsTable.receiverId]
            cancelledCountByUser[sId] = (cancelledCountByUser[sId] ?: 0) + 1
            cancelledCountByUser[rId] = (cancelledCountByUser[rId] ?: 0) + 1
        }

        val candidates = allProfiles.map { profileRow ->
            val uId = profileRow[ProfilesTable.userId]
            val scores = ratingsByUser[uId] ?: emptyList()
            val completed = completedCountByUser[uId] ?: 0
            val cancelled = cancelledCountByUser[uId] ?: 0

            val rep = ReputationCalculator.calculate(
                ratings = scores,
                completedCount = completed,
                cancelledAfterAcceptedCount = cancelled
            )

            LeaderboardCandidate(
                userId = uId,
                fullName = profileRow[ProfilesTable.fullName],
                avatarUrl = profileRow[ProfilesTable.avatarUrl],
                city = profileRow[ProfilesTable.city],
                score = rep.score ?: 0.0,
                badge = rep.badge,
                totalExchanges = rep.totalExchanges,
                ratingCount = rep.ratingCount
            )
        }

        StatsCalculator.rankLeaderboard(candidates)
    }
}
