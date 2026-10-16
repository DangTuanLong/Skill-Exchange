package com.skillexchange.api.services.graph

import com.skillexchange.api.models.graph.GraphUserNodeDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillGraphServiceTest {

    private val service = SkillGraphService()

    @Test
    fun `prove seed 3-way circular loop uiux to music to lang to uiux with HAVE greater or equal to WANT`() = runBlocking {
        // ─────────────────────────────────────────────────────────────
        // 1. Dựng dữ liệu tương đương chính xác 3 personas kịch bản trong DemoDataGenerator.kt:
        //    - uiux: HAVE Figma (Lv 4), WANT Tiếng Anh giao tiếp (Lv 2)
        //    - music: HAVE Guitar (Lv 3), WANT Figma (Lv 2)
        //    - lang: HAVE Tiếng Anh giao tiếp (Lv 5), WANT Guitar (Lv 1)
        // ─────────────────────────────────────────────────────────────
        val uiuxUser = GraphUserNodeDto(userId = "user-uiux", fullName = "Lê Thu Trang (UI/UX)", city = "TP.HCM")
        val musicUser = GraphUserNodeDto(userId = "user-music", fullName = "Phạm Thuỳ Linh (Music)", city = "Hà Nội")
        val langUser = GraphUserNodeDto(userId = "user-lang", fullName = "Hoàng Văn Đức (Language)", city = "Đà Nẵng")
        val allNodes = listOf(uiuxUser, musicUser, langUser)

        // Các cạnh sinh từ điều kiện HAVE >= WANT:
        // Cạnh 1: uiux dạy Figma (Lv 4) cho music (cần Figma Lv 2) -> 4 >= 2 (Hợp lệ)
        val edge1 = EdgeCandidate(
            fromUserId = "user-uiux",
            toUserId = "user-music",
            skillId = 1,
            skillName = "Figma",
            haveLevel = 4,
            wantLevel = 2
        )
        // Cạnh 2: music dạy Guitar (Lv 3) cho lang (cần Guitar Lv 1) -> 3 >= 1 (Hợp lệ)
        val edge2 = EdgeCandidate(
            fromUserId = "user-music",
            toUserId = "user-lang",
            skillId = 2,
            skillName = "Guitar",
            haveLevel = 3,
            wantLevel = 1
        )
        // Cạnh 3: lang dạy Tiếng Anh giao tiếp (Lv 5) cho uiux (cần Tiếng Anh giao tiếp Lv 2) -> 5 >= 2 (Hợp lệ)
        val edge3 = EdgeCandidate(
            fromUserId = "user-lang",
            toUserId = "user-uiux",
            skillId = 3,
            skillName = "Tiếng Anh giao tiếp",
            haveLevel = 5,
            wantLevel = 2
        )
        val rawEdges = listOf(edge1, edge2, edge3)

        // ─────────────────────────────────────────────────────────────
        // 2. Chạy thuật toán tìm chu trình neo tại "user-uiux"
        // ─────────────────────────────────────────────────────────────
        val response = service.computeCycles(
            callerId = "user-uiux",
            nodes = allNodes,
            rawEdges = rawEdges,
            maxDepth = 3,
            limit = 5
        )

        // ─────────────────────────────────────────────────────────────
        // 3. Khẳng định kết quả: Phải phát hiện đúng 1 chu trình 3 người hoàn hảo
        // ─────────────────────────────────────────────────────────────
        assertEquals(0, response.loops2.size, "Không có chu trình 2 người song phương")
        assertEquals(1, response.loops3Plus.size, "Phải phát hiện chính xác 1 chu trình 3 người")

        val loop = response.loops3Plus.first()
        assertEquals(3, loop.cycleLength, "Chu trình phải có độ dài 3")
        assertEquals(3, loop.nodes.size, "Chu trình phải gồm 3 đỉnh")
        assertEquals(3, loop.edges.size, "Chu trình phải gồm 3 cạnh")

        // Kiểm tra thứ tự các đỉnh trong chu trình
        assertEquals("user-uiux", loop.nodes[0].userId)
        assertEquals("user-music", loop.nodes[1].userId)
        assertEquals("user-lang", loop.nodes[2].userId)

        // Kiểm tra đúng chiều và đúng kỹ năng truyền thụ từng chặng
        // Chặng 1: uiux -> music (Figma)
        assertEquals("user-uiux", loop.edges[0].fromUserId)
        assertEquals("user-music", loop.edges[0].toUserId)
        assertEquals("Figma", loop.edges[0].skillName)
        assertTrue(loop.edges[0].haveLevel >= loop.edges[0].wantLevel, "uiux HAVE Figma phải >= music WANT Figma")

        // Chặng 2: music -> lang (Guitar)
        assertEquals("user-music", loop.edges[1].fromUserId)
        assertEquals("user-lang", loop.edges[1].toUserId)
        assertEquals("Guitar", loop.edges[1].skillName)
        assertTrue(loop.edges[1].haveLevel >= loop.edges[1].wantLevel, "music HAVE Guitar phải >= lang WANT Guitar")

        // Chặng 3: lang -> uiux (Tiếng Anh giao tiếp)
        assertEquals("user-lang", loop.edges[2].fromUserId)
        assertEquals("user-uiux", loop.edges[2].toUserId)
        assertEquals("Tiếng Anh giao tiếp", loop.edges[2].skillName)
        assertTrue(loop.edges[2].haveLevel >= loop.edges[2].wantLevel, "lang HAVE Tiếng Anh phải >= uiux WANT Tiếng Anh")
    }

    @Test
    fun `prove seed bilateral 2-way loop sender and receiver`() = runBlocking {
        // sender: HAVE Kotlin (Lv 4), WANT Python (Lv 2)
        // receiver: HAVE Python (Lv 3), WANT Kotlin (Lv 2)
        val sender = GraphUserNodeDto("user-sender", "Đặng Tuấn Long (Sender)")
        val receiver = GraphUserNodeDto("user-receiver", "Nguyễn Minh Quân (Receiver)")

        val rawEdges = listOf(
            EdgeCandidate("user-sender", "user-receiver", 10, "Kotlin Android", 4, 2),
            EdgeCandidate("user-receiver", "user-sender", 20, "Python", 3, 2)
        )

        val response = service.computeCycles(
            callerId = "user-sender",
            nodes = listOf(sender, receiver),
            rawEdges = rawEdges,
            maxDepth = 3,
            limit = 5
        )

        assertEquals(1, response.loops2.size, "Phải tìm thấy 1 chu trình song phương 2 người")
        assertEquals(0, response.loops3Plus.size)

        val loop2 = response.loops2.first()
        assertEquals(2, loop2.cycleLength)
        assertEquals("user-sender", loop2.edges[0].fromUserId)
        assertEquals("user-receiver", loop2.edges[0].toUserId)
        assertEquals("Kotlin Android", loop2.edges[0].skillName)

        assertEquals("user-receiver", loop2.edges[1].fromUserId)
        assertEquals("user-sender", loop2.edges[1].toUserId)
        assertEquals("Python", loop2.edges[1].skillName)
    }

    @Test
    fun `deduplicate multiple valid skills on an edge using Choice A`() = runBlocking {
        // Alice có thể dạy cả Photoshop (4 >= 2, diff = 2) lẫn Illustrator (5 >= 1, diff = 4) cho Bob
        // Bob dạy Python cho Alice (3 >= 2)
        val alice = GraphUserNodeDto("alice", "Alice")
        val bob = GraphUserNodeDto("bob", "Bob")

        val rawEdges = listOf(
            EdgeCandidate("alice", "bob", 1, "Photoshop", 4, 2),
            EdgeCandidate("alice", "bob", 2, "Illustrator", 5, 1),
            EdgeCandidate("bob", "alice", 3, "Python", 3, 2)
        )

        val response = service.computeCycles(
            callerId = "alice",
            nodes = listOf(alice, bob),
            rawEdges = rawEdges,
            maxDepth = 2,
            limit = 5
        )

        // Không được nhân đôi thành 2 chu trình! Chỉ 1 chu trình duy nhất
        assertEquals(1, response.loops2.size)
        val loop = response.loops2.first()
        val aliceEdge = loop.edges.first { it.fromUserId == "alice" }

        // Kỹ năng có diff cao nhất (Illustrator, diff = 4) phải làm primary
        assertEquals("Illustrator", aliceEdge.skillName)
        assertEquals(1, aliceEdge.alternativeSkills.size)
        assertEquals("Photoshop", aliceEdge.alternativeSkills.first().skillName)
    }

    @Test
    fun `ignore paths that violate HAVE greater or equal to WANT`() = runBlocking {
        // A -> B (HAVE 2 < WANT 3: vi phạm điều kiện)
        // B -> C (HAVE 4 >= WANT 2)
        // C -> A (HAVE 3 >= WANT 1)
        val a = GraphUserNodeDto("A", "User A")
        val b = GraphUserNodeDto("B", "User B")
        val c = GraphUserNodeDto("C", "User C")

        // A -> B không sinh cạnh candidate vì HAVE < WANT
        val rawEdges = listOf(
            EdgeCandidate("b", "c", 1, "Guitar", 4, 2),
            EdgeCandidate("c", "a", 2, "Piano", 3, 1)
        )

        val response = service.computeCycles(
            callerId = "a",
            nodes = listOf(a, b, c),
            rawEdges = rawEdges,
            maxDepth = 3,
            limit = 5
        )

        assertTrue(response.loops2.isEmpty())
        assertTrue(response.loops3Plus.isEmpty())
    }
}
