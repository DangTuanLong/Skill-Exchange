package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.stats.LeaderboardItemDto
import com.skillexchange.app.data.remote.stats.StatsRemoteDataSource
import com.skillexchange.app.data.remote.stats.UserStatsDto
import com.skillexchange.app.domain.repository.IStatsRepository

class StatsRepositoryImpl(
    private val remoteDataSource: StatsRemoteDataSource,
    private val tokenManager: TokenManager
) : IStatsRepository {

    override suspend fun getUserStats(): Result<UserStatsDto> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        val response = remoteDataSource.getUserStats(token)
        if (!response.success) {
            throw RuntimeException("Tải thống kê cá nhân thất bại")
        }
        response.data
    }

    override suspend fun getLeaderboard(): Result<List<LeaderboardItemDto>> = runCatching {
        val token = tokenManager.getAccessToken()
        val response = remoteDataSource.getLeaderboard(token)
        if (!response.success) {
            throw RuntimeException("Tải bảng xếp hạng thất bại")
        }
        response.data
    }
}
