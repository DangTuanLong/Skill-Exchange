package com.skillexchange.app.data.remote.graph

import kotlinx.serialization.Serializable

@Serializable
data class GraphUserNodeDto(
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null
)

@Serializable
data class AlternativeSkillDto(
    val skillId: Int,
    val skillName: String,
    val haveLevel: Int,
    val wantLevel: Int
)

@Serializable
data class GraphEdgeDto(
    val fromUserId: String,
    val toUserId: String,
    val skillId: Int,
    val skillName: String,
    val haveLevel: Int,
    val wantLevel: Int,
    val alternativeSkills: List<AlternativeSkillDto> = emptyList()
)

@Serializable
data class SuggestedLoopDto(
    val loopId: String,
    val cycleLength: Int,
    val nodes: List<GraphUserNodeDto>,
    val edges: List<GraphEdgeDto>
)

@Serializable
data class SuggestPathResponseDto(
    val success: Boolean = true,
    val callerId: String,
    val loops2: List<SuggestedLoopDto> = emptyList(),
    val loops3Plus: List<SuggestedLoopDto> = emptyList(),
    val disclaimer: String = "Trao đổi vòng tròn là gợi ý kết nối gián tiếp. Hãy gửi yêu cầu trao đổi 1-1 tới đối tác liên quan."
)
