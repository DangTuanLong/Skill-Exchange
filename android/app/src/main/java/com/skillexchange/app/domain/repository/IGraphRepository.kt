package com.skillexchange.app.domain.repository

data class GraphUserNode(
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null
)

data class AlternativeSkill(
    val skillId: Int,
    val skillName: String,
    val haveLevel: Int,
    val wantLevel: Int
)

data class GraphEdge(
    val fromUserId: String,
    val toUserId: String,
    val skillId: Int,
    val skillName: String,
    val haveLevel: Int,
    val wantLevel: Int,
    val alternativeSkills: List<AlternativeSkill> = emptyList()
)

data class SuggestedLoop(
    val loopId: String,
    val cycleLength: Int,
    val nodes: List<GraphUserNode>,
    val edges: List<GraphEdge>
)

data class SuggestPathResult(
    val callerId: String,
    val loops2: List<SuggestedLoop>,
    val loops3Plus: List<SuggestedLoop>,
    val disclaimer: String
)

interface IGraphRepository {
    suspend fun getSuggestedPaths(depth: Int = 3, limit: Int = 5): Result<SuggestPathResult>
}
