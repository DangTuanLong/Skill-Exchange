package com.skillexchange.app.data.remote.stats

import kotlinx.serialization.Serializable

@Serializable
data class MonthlySessionDto(
    val month: String,
    val label: String,
    val count: Int
)

@Serializable
data class TimelineItemDto(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val timestamp: String
)

@Serializable
data class RadarAxisDto(
    val axisId: Int,
    val categoryName: String,
    val score: Double,
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
    val monthlySessions: List<MonthlySessionDto> = emptyList(),
    val timeline: List<TimelineItemDto> = emptyList(),
    val radarScores: List<RadarAxisDto> = emptyList(),
    val skillGapSuggestions: List<SkillGapSuggestionDto> = emptyList()
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
data class UserStatsResponseDto(
    val success: Boolean,
    val data: UserStatsDto
)

@Serializable
data class LeaderboardResponseDto(
    val success: Boolean,
    val data: List<LeaderboardItemDto>
)
