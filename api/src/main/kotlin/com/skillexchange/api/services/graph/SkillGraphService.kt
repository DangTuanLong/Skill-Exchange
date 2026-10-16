package com.skillexchange.api.services.graph

import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.models.graph.AlternativeSkillDto
import com.skillexchange.api.models.graph.GraphEdgeDto
import com.skillexchange.api.models.graph.GraphUserNodeDto
import com.skillexchange.api.models.graph.SuggestPathResponse
import com.skillexchange.api.models.graph.SuggestedLoopDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class GraphTimeoutException(message: String) : RuntimeException(message)

data class EdgeCandidate(
    val fromUserId: String,
    val toUserId: String,
    val skillId: Int,
    val skillName: String,
    val haveLevel: Int,
    val wantLevel: Int
)

data class AggregatedEdge(
    val fromUserId: String,
    val toUserId: String,
    val primarySkillId: Int,
    val primarySkillName: String,
    val primaryHaveLevel: Int,
    val primaryWantLevel: Int,
    val alternativeSkills: List<AlternativeSkillDto>
)

class SkillGraphService(
    private val memoryGraphDataProvider: (() -> Pair<List<GraphUserNodeDto>, List<EdgeCandidate>>)? = null
) {

    /**
     * Tìm kiếm chu trình trao đổi kỹ năng neo tại [callerId].
     * @param callerId UUID của người dùng hiện tại
     * @param maxDepth Độ sâu chu trình tối đa (2..4, mặc định 3)
     * @param limit Số lượng chu trình tối đa mỗi nhóm (mặc định 5)
     * @param timeoutMs Thời gian tối đa cho phép xử lý (mặc định 2000ms)
     */
    suspend fun findLoops(
        callerId: String,
        maxDepth: Int = 3,
        limit: Int = 5,
        timeoutMs: Long = 2000L
    ): SuggestPathResponse = withContext(Dispatchers.IO) {
        val boundedDepth = maxDepth.coerceIn(2, 4)
        val boundedLimit = limit.coerceIn(1, 20)

        val result = withTimeoutOrNull(timeoutMs) {
            if (memoryGraphDataProvider != null) {
                val (nodes, rawEdges) = memoryGraphDataProvider.invoke()
                computeCycles(callerId, nodes, rawEdges, boundedDepth, boundedLimit)
            } else {
                fetchAndComputeFromDatabase(callerId, boundedDepth, boundedLimit)
            }
        } ?: throw GraphTimeoutException("Quá thời gian xử lý truy vấn đồ thị (${timeoutMs}ms)")

        result
    }

    /**
     * Nạp dữ liệu các đỉnh liên quan và cạnh có hướng từ PostgreSQL qua Exposed.
     */
    private fun fetchAndComputeFromDatabase(
        callerId: String,
        maxDepth: Int,
        limit: Int
    ): SuggestPathResponse = transaction {
        // 1. Tải danh sách kỹ năng HAVE và WANT
        val userSkillsQuery = (UserSkillsTable innerJoin SkillsTable)
            .selectAll()
            .map { row ->
                RawUserSkillRecord(
                    userId = row[UserSkillsTable.userId],
                    skillId = row[UserSkillsTable.skillId],
                    skillName = row[SkillsTable.name],
                    type = row[UserSkillsTable.type],
                    proficiencyLevel = row[UserSkillsTable.proficiencyLevel]
                )
            }

        // 2. Tải thông tin hồ sơ người dùng
        val profilesMap = ProfilesTable.selectAll()
            .associate { row ->
                val uid = row[ProfilesTable.userId]
                uid to GraphUserNodeDto(
                    userId = uid,
                    fullName = row[ProfilesTable.fullName],
                    avatarUrl = row[ProfilesTable.avatarUrl],
                    city = row[ProfilesTable.city]
                )
            }

        // 3. Xây dựng tập cạnh có hướng thỏa mãn HAVE >= WANT và A <> B
        val haveMap = userSkillsQuery.filter { it.type == "HAVE" }
            .groupBy { it.skillId }
        val wantList = userSkillsQuery.filter { it.type == "WANT" }

        val candidateEdges = mutableListOf<EdgeCandidate>()
        for (w in wantList) {
            val potentialTeachers = haveMap[w.skillId] ?: emptyList()
            for (h in potentialTeachers) {
                if (h.userId != w.userId && h.proficiencyLevel >= w.proficiencyLevel) {
                    candidateEdges.add(
                        EdgeCandidate(
                            fromUserId = h.userId,
                            toUserId = w.userId,
                            skillId = h.skillId,
                            skillName = h.skillName,
                            haveLevel = h.proficiencyLevel,
                            wantLevel = w.proficiencyLevel
                        )
                    )
                }
            }
        }

        val allNodes = profilesMap.values.toList()
        computeCycles(callerId, allNodes, candidateEdges, maxDepth, limit)
    }

    /**
     * Thuật toán thuần túy tìm chu trình neo tại [callerId].
     * - Khử trùng lặp chu trình cùng tập người dùng (Lựa chọn A: gộp kỹ năng trên cạnh).
     * - Khống chế số cạnh mở rộng mỗi đỉnh (tối đa 15 cạnh tốt nhất).
     * - Sử dụng mảng visited chống chu trình lặp vô hạn.
     */
    fun computeCycles(
        callerId: String,
        nodes: List<GraphUserNodeDto>,
        rawEdges: List<EdgeCandidate>,
        maxDepth: Int,
        limit: Int,
        maxEdgesPerNode: Int = 15
    ): SuggestPathResponse {
        val nodeMap = nodes.associateBy { it.userId }

        // Gộp các kỹ năng trên cùng một cặp (From, To) thành 1 cạnh duy nhất (Lựa chọn A)
        // Sắp xếp các kỹ năng theo (haveLevel - wantLevel) giảm dần -> chọn top 1 làm primarySkill
        val aggregatedEdges = rawEdges
            .groupBy { Pair(it.fromUserId, it.toUserId) }
            .map { (pair, edgeList) ->
                val sorted = edgeList.sortedByDescending { it.haveLevel - it.wantLevel }
                val best = sorted.first()
                val alternatives = sorted.drop(1).map {
                    AlternativeSkillDto(
                        skillId = it.skillId,
                        skillName = it.skillName,
                        haveLevel = it.haveLevel,
                        wantLevel = it.wantLevel
                    )
                }
                AggregatedEdge(
                    fromUserId = pair.first,
                    toUserId = pair.second,
                    primarySkillId = best.skillId,
                    primarySkillName = best.skillName,
                    primaryHaveLevel = best.haveLevel,
                    primaryWantLevel = best.wantLevel,
                    alternativeSkills = alternatives
                )
            }

        // Tạo danh sách kề (Adjacency List) có giới hạn số cạnh mở rộng mỗi đỉnh
        val adjMap = aggregatedEdges
            .groupBy { it.fromUserId }
            .mapValues { (_, edges) ->
                edges.sortedByDescending { it.primaryHaveLevel - it.primaryWantLevel }
                    .take(maxEdgesPerNode)
            }

        val foundCycles = mutableListOf<List<AggregatedEdge>>()
        val visited = mutableSetOf<String>()

        fun dfs(currentUserId: String, path: List<AggregatedEdge>) {
            val currentDepth = path.size
            if (currentDepth >= maxDepth) return

            val outgoing = adjMap[currentUserId] ?: return
            for (edge in outgoing) {
                if (edge.toUserId == callerId) {
                    // Đóng chu trình hợp lệ neo tại callerId!
                    val completeCycle = path + edge
                    if (completeCycle.size in 2..maxDepth) {
                        foundCycles.add(completeCycle)
                    }
                } else if (!visited.contains(edge.toUserId) && currentDepth + 1 < maxDepth) {
                    visited.add(edge.toUserId)
                    dfs(edge.toUserId, path + edge)
                    visited.remove(edge.toUserId)
                }
            }
        }

        // Bắt đầu DFS từ callerId
        visited.add(callerId)
        val callerOutgoing = adjMap[callerId] ?: emptyList()
        for (edge in callerOutgoing) {
            if (edge.toUserId == callerId) continue // Tự trao đổi với chính mình bị bỏ qua
            visited.add(edge.toUserId)
            dfs(edge.toUserId, listOf(edge))
            visited.remove(edge.toUserId)
        }

        // Chuyển đổi thành DTOs và tách riêng vòng 2 người và vòng 3-4 người
        val loops2 = mutableListOf<SuggestedLoopDto>()
        val loops3Plus = mutableListOf<SuggestedLoopDto>()

        for (cycle in foundCycles) {
            val cycleLength = cycle.size
            val userIdsInCycle = cycle.map { it.fromUserId }
            val cycleNodes = userIdsInCycle.map { uid ->
                nodeMap[uid] ?: GraphUserNodeDto(userId = uid, fullName = "Thành viên")
            }
            val cycleEdges = cycle.map { e ->
                GraphEdgeDto(
                    fromUserId = e.fromUserId,
                    toUserId = e.toUserId,
                    skillId = e.primarySkillId,
                    skillName = e.primarySkillName,
                    haveLevel = e.primaryHaveLevel,
                    wantLevel = e.primaryWantLevel,
                    alternativeSkills = e.alternativeSkills
                )
            }

            val loopDto = SuggestedLoopDto(
                loopId = UUID.randomUUID().toString(),
                cycleLength = cycleLength,
                nodes = cycleNodes,
                edges = cycleEdges
            )

            if (cycleLength == 2 && loops2.size < limit) {
                loops2.add(loopDto)
            } else if (cycleLength in 3..4 && loops3Plus.size < limit) {
                loops3Plus.add(loopDto)
            }

            if (loops2.size >= limit && loops3Plus.size >= limit) {
                break
            }
        }

        return SuggestPathResponse(
            success = true,
            callerId = callerId,
            loops2 = loops2,
            loops3Plus = loops3Plus
        )
    }

    private data class RawUserSkillRecord(
        val userId: String,
        val skillId: Int,
        val skillName: String,
        val type: String,
        val proficiencyLevel: Int
    )
}
