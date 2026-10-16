package com.skillexchange.api.models.stats

import kotlinx.serialization.Serializable

@Serializable
data class MonthlySessionDto(
    val month: String,      // YYYY-MM
    val label: String,      // MM/YYYY
    val count: Int
)

@Serializable
data class TimelineItemDto(
    val id: String,
    val type: String,       // "EXCHANGE" hoặc "RATING"
    val title: String,
    val description: String,
    val timestamp: String
)

@Serializable
data class RadarAxisDto(
    val axisId: Int,
    val categoryName: String,
    val score: Double,      // 0.0..5.0
    val skillCount: Int
)

@Serializable
data class SkillGapSuggestionDto(
    val skillId: Int,
    val skillName: String,
    val categoryName: String,
    val unlockedMatchesCount: Int,
    val reason: String
)

@Serializable
data class UserStatsDto(
    val totalSessions: Int,
    val avgRating: Double,
    val ratingCount: Int,
    val completionRate: Double,
    val monthlySessions: List<MonthlySessionDto>,
    val timeline: List<TimelineItemDto>,
    val radarScores: List<RadarAxisDto>,
    val skillGapSuggestions: List<SkillGapSuggestionDto>
)

@Serializable
data class LeaderboardItemDto(
    val rank: Int,
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null,
    val score: Double,
    val badge: String? = null,
    val totalExchanges: Int,
    val ratingCount: Int
)

@Serializable
data class UserStatsResponse(
    val success: Boolean = true,
    val data: UserStatsDto
)

@Serializable
data class LeaderboardResponse(
    val success: Boolean = true,
    val data: List<LeaderboardItemDto>
)
