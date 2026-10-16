package com.skillexchange.app.presentation.dashboard

import com.skillexchange.app.data.remote.stats.LeaderboardItemDto
import com.skillexchange.app.data.remote.stats.UserStatsDto

data class DashboardState(
    val isLoading: Boolean = true,
    val userStats: UserStatsDto? = null,
    val leaderboard: List<LeaderboardItemDto> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && (userStats == null || userStats.totalSessions == 0 && userStats.radarScores.all { it.score == 0.0 })
}

sealed interface DashboardIntent {
    data object LoadData : DashboardIntent
    data object Refresh : DashboardIntent
    data class ClickLeaderboardUser(val userId: String) : DashboardIntent
    data class ClickSkillGap(val skillId: Int, val skillName: String) : DashboardIntent
}

sealed interface DashboardEffect {
    data class NavigateToProfile(val userId: String) : DashboardEffect
    data class ShowSnackbar(val message: String) : DashboardEffect
}
