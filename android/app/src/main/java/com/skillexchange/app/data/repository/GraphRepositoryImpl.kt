package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.graph.GraphRemoteDataSource
import com.skillexchange.app.domain.repository.AlternativeSkill
import com.skillexchange.app.domain.repository.GraphEdge
import com.skillexchange.app.domain.repository.GraphUserNode
import com.skillexchange.app.domain.repository.IGraphRepository
import com.skillexchange.app.domain.repository.SuggestPathResult
import com.skillexchange.app.domain.repository.SuggestedLoop

class GraphRepositoryImpl(
    private val remoteDataSource: GraphRemoteDataSource,
    private val tokenManager: TokenManager
) : IGraphRepository {

    override suspend fun getSuggestedPaths(depth: Int, limit: Int): Result<SuggestPathResult> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        val response = remoteDataSource.getSuggestedPaths(token, depth, limit)
        if (!response.success) {
            throw RuntimeException("Tải gợi ý chu trình thất bại")
        }

        fun mapLoop(dto: com.skillexchange.app.data.remote.graph.SuggestedLoopDto) = SuggestedLoop(
            loopId = dto.loopId,
            cycleLength = dto.cycleLength,
            nodes = dto.nodes.map {
                GraphUserNode(
                    userId = it.userId,
                    fullName = it.fullName,
                    avatarUrl = it.avatarUrl,
                    city = it.city
                )
            },
            edges = dto.edges.map {
                GraphEdge(
                    fromUserId = it.fromUserId,
                    toUserId = it.toUserId,
                    skillId = it.skillId,
                    skillName = it.skillName,
                    haveLevel = it.haveLevel,
                    wantLevel = it.wantLevel,
                    alternativeSkills = it.alternativeSkills.map { alt ->
                        AlternativeSkill(
                            skillId = alt.skillId,
                            skillName = alt.skillName,
                            haveLevel = alt.haveLevel,
                            wantLevel = alt.wantLevel
                        )
                    }
                )
            }
        )

        SuggestPathResult(
            callerId = response.callerId,
            loops2 = response.loops2.map { mapLoop(it) },
            loops3Plus = response.loops3Plus.map { mapLoop(it) },
            disclaimer = response.disclaimer
        )
    }
}
