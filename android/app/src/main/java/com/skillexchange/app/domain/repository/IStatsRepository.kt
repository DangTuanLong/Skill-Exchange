package com.skillexchange.app.domain.repository

import com.skillexchange.app.data.remote.stats.LeaderboardItemDto
import com.skillexchange.app.data.remote.stats.UserStatsDto

interface IStatsRepository {
    suspend fun getUserStats(): Result<UserStatsDto>
    suspend fun getLeaderboard(): Result<List<LeaderboardItemDto>>
}
