package com.skillexchange.api.services.nlp

import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.nlp.ExtractedSkillDto
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

data class SkillDefinition(
    val id: Int,
    val name: String,
    val categoryId: Int? = null,
    val categoryName: String? = null
)

class SkillExtractionService(
    private val skillProvider: (() -> List<SkillDefinition>)? = null
) {
    // Cache danh sách kỹ năng hệ thống trong bộ nhớ
    @Volatile
    private var cachedSkills: List<SkillDefinition>? = null
    private var lastCacheTime: Long = 0
    private val CACHE_TTL_MS = 60_000L // 1 phút

    /**
     * Tự nạp kỹ năng từ DB hoặc từ provider được cấu hình.
     */
    fun getSkillDefinitions(): List<SkillDefinition> {
        val now = System.currentTimeMillis()
        val currentCache = cachedSkills
        if (currentCache != null && (now - lastCacheTime) < CACHE_TTL_MS) {
            return currentCache
        }

        val loaded = if (skillProvider != null) {
            skillProvider.invoke()
        } else {
            try {
                transaction {
                    (SkillsTable innerJoin SkillCategoriesTable).selectAll().map {
                        SkillDefinition(
                            id = it[SkillsTable.id],
                            name = it[SkillsTable.name],
                            categoryId = it[SkillsTable.categoryId],
                            categoryName = it[SkillCategoriesTable.name]
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback khi bảng chưa có kết nối DB (ví dụ trong unit test không kết nối DB)
                currentCache ?: defaultSkills()
            }
        }

        val finalSkills = loaded.ifEmpty { defaultSkills() }
        cachedSkills = finalSkills
        lastCacheTime = now
        return finalSkills
    }

    /**
     * Chuẩn hóa Unicode NFC.
     */
    fun normalizeNfc(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
    }

    /**
     * Loại bỏ dấu tiếng Việt (Unaccent) và chuyển về chữ thường.
     * Xử lý cả NFD combining marks và ký tự 'đ'/'Đ'.
     */
    fun unaccent(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutMarks = nfd.replace(Regex("\\p{M}"), "")
        return withoutMarks
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase()
    }

    // Loại trừ các từ tiếng Việt thông dụng / giới từ khỏi danh sách mục tiêu fuzzy để tránh dương tính giả.
    // Ví dụ: "trong" (Trống) trùng giới từ tiếng Việt và chỉ cách các từ như "thống", "trung", "trường" đúng 1 ký tự.
    private val FUZZY_EXCLUDED_TARGETS = setOf("trong")

    /**
     * Thuật toán Damerau-Levenshtein Distance thuần Kotlin (Zero dependency).
     * Hỗ trợ chèn (insertion), xóa (deletion), thay thế (substitution) và hoán vị 2 ký tự liền kề (transposition).
     */
    fun damerauLevenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val d = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) d[i][0] = i
        for (j in 0..s2.length) d[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,       // deletion
                    d[i][j - 1] + 1,       // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1) // transposition
                }
            }
        }
        return d[s1.length][s2.length]
    }

    /**
     * Tạo regex pattern có ranh giới từ chặt chẽ Unicode:
     * Lookbehind: (?<![\p{L}\p{N}])
     * Lookahead: (?![\p{L}\p{N}])
     * Xử lý riêng biệt cho "c", "c++", "c#" để không bắt nhầm.
     */
    fun buildWordBoundaryRegex(rawTerm: String): Regex {
        val normalized = rawTerm.trim().lowercase()
        return when (normalized) {
            "c" -> Regex("(?<![\\p{L}\\p{N}])c(?![\\p{L}\\p{N}+#])", RegexOption.IGNORE_CASE)
            "c++", "cpp" -> Regex("(?<![\\p{L}\\p{N}])(?:c\\+\\+|cpp)(?![\\p{L}\\p{N}+])", RegexOption.IGNORE_CASE)
            "c#", "csharp" -> Regex("(?<![\\p{L}\\p{N}])(?:c#|csharp)(?![\\p{L}\\p{N}#])", RegexOption.IGNORE_CASE)
            else -> {
                val escaped = Regex.escape(normalized)
                Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            }
        }
    }

    /**
     * Trích xuất kỹ năng từ văn bản Bio theo phân tầng confidence:
     * - Tầng 1: 1.0 (Exact match tên kỹ năng chuẩn trong DB)
     * - Tầng 2: 0.9 (Alias match từ bảng từ đồng nghĩa thật)
     * - Tầng 3: 0.75 (Fuzzy match Levenshtein <= 1 cho từ >= 5 ký tự)
     */
    fun extractSkills(rawText: String): List<ExtractedSkillDto> {
        val nfcText = normalizeNfc(rawText)
        val unaccentedBio = unaccent(nfcText)
        val skills = getSkillDefinitions()

        // Map kết quả tạm: skillId -> ExtractedSkillDto
        val resultMap = mutableMapOf<Int, ExtractedSkillDto>()

        // 1. TẦNG 1: EXACT MATCH TÊN KỸ NĂNG CHUẨN TRONG DB (Confidence: 1.0)
        for (skill in skills) {
            val unaccentedName = unaccent(skill.name)
            if (unaccentedName.isBlank()) continue
            val regex = buildWordBoundaryRegex(unaccentedName)
            if (regex.containsMatchIn(unaccentedBio)) {
                val existing = resultMap[skill.id]
                if (existing == null || existing.confidence < 1.0) {
                    resultMap[skill.id] = ExtractedSkillDto(
                        skillId = skill.id,
                        skillName = skill.name,
                        categoryId = skill.categoryId,
                        categoryName = skill.categoryName,
                        confidence = 1.0,
                        matchType = "EXACT",
                        matchedTerm = skill.name
                    )
                }
            }
        }

        // 2. TẦNG 2: ALIAS MATCH TỪ ĐỒNG NGHĨA THẬT (Confidence: 0.9)
        for (skill in skills) {
            // Tìm các alias tương ứng cho skill này
            val aliases = findAliasesForSkill(skill.name)
            for (alias in aliases) {
                val unaccentedAlias = unaccent(alias)
                val regex = buildWordBoundaryRegex(unaccentedAlias)
                if (regex.containsMatchIn(unaccentedBio)) {
                    val existing = resultMap[skill.id]
                    if (existing == null || existing.confidence < 0.9) {
                        resultMap[skill.id] = ExtractedSkillDto(
                            skillId = skill.id,
                            skillName = skill.name,
                            categoryId = skill.categoryId,
                            categoryName = skill.categoryName,
                            confidence = 0.9,
                            matchType = "ALIAS",
                            matchedTerm = alias
                        )
                    }
                    break
                }
            }
        }

        // 3. TẦNG 3: FUZZY MATCH CHO TỪ ĐƠN >= 5 KÝ TỰ, LEVENSHTEIN <= 1 (Confidence: 0.75)
        // Lấy tất cả các từ trong unaccentedBio
        val bioTokens = unaccentedBio
            .split("[^\\p{L}\\p{N}]+".toRegex())
            .filter { it.length >= 5 }

        // Tập các từ mục tiêu để fuzzy match (từ đơn >= 5 ký tự)
        val singleWordTargets = mutableListOf<Pair<String, SkillDefinition>>()
        for (skill in skills) {
            val unaccentedName = unaccent(skill.name)
            // Nếu tên là 1 từ >= 5 ký tự
            if (!unaccentedName.contains(" ") && unaccentedName.length >= 5) {
                singleWordTargets.add(unaccentedName to skill)
            }
            // Thêm các alias đơn từ >= 5 ký tự
            val aliases = findAliasesForSkill(skill.name)
            for (alias in aliases) {
                val unaccentedAlias = unaccent(alias)
                if (!unaccentedAlias.contains(" ") && unaccentedAlias.length >= 5) {
                    singleWordTargets.add(unaccentedAlias to skill)
                }
            }
        }

        for (token in bioTokens) {
            for ((targetWord, skill) in singleWordTargets) {
                // Bỏ qua các từ mục tiêu bị loại trừ (trùng giới từ phổ biến)
                if (targetWord in FUZZY_EXCLUDED_TARGETS) continue

                // Nếu đã được match với confidence cao hơn thì bỏ qua
                val current = resultMap[skill.id]
                if (current != null && current.confidence >= 0.9) continue

                // Chỉ so sánh nếu độ dài chênh lệch không quá 1
                if (Math.abs(token.length - targetWord.length) <= 1) {
                    val dist = damerauLevenshteinDistance(token, targetWord)
                    if (dist == 1) { // dist == 0 đã được xử lý ở Tầng 1/2
                        resultMap[skill.id] = ExtractedSkillDto(
                            skillId = skill.id,
                            skillName = skill.name,
                            categoryId = skill.categoryId,
                            categoryName = skill.categoryName,
                            confidence = 0.75,
                            matchType = "FUZZY",
                            matchedTerm = "$token ~ $targetWord"
                        )
                    }
                }
            }
        }

        // Sắp xếp kết quả theo confidence giảm dần, sau đó theo ID tăng dần
        return resultMap.values
            .sortedWith(compareByDescending<ExtractedSkillDto> { it.confidence }.thenBy { it.skillId })
    }

    private fun findAliasesForSkill(skillName: String): List<String> {
        val unaccented = unaccent(skillName)
        for ((canonical, aliases) in SkillAliases.CANONICAL_TO_ALIASES) {
            if (unaccent(canonical) == unaccented) {
                return aliases
            }
        }
        return emptyList()
    }

    private fun defaultSkills(): List<SkillDefinition> = listOf(
        SkillDefinition(1, "Android (Kotlin)", 1, "Lập trình"),
        SkillDefinition(2, "iOS (Swift)", 1, "Lập trình"),
        SkillDefinition(3, "Web (React)", 1, "Lập trình"),
        SkillDefinition(4, "Web (Vue)", 1, "Lập trình"),
        SkillDefinition(5, "Python", 1, "Lập trình"),
        SkillDefinition(6, "Java", 1, "Lập trình"),
        SkillDefinition(7, "Flutter", 1, "Lập trình"),
        SkillDefinition(8, "Node.js", 1, "Lập trình"),
        SkillDefinition(9, "SQL", 1, "Lập trình"),
        SkillDefinition(10, "AI/ML", 1, "Lập trình"),
        SkillDefinition(11, "Tiếng Anh", 2, "Ngoại ngữ"),
        SkillDefinition(12, "Tiếng Nhật", 2, "Ngoại ngữ"),
        SkillDefinition(13, "Tiếng Hàn", 2, "Ngoại ngữ"),
        SkillDefinition(14, "Tiếng Trung", 2, "Ngoại ngữ"),
        SkillDefinition(15, "Tiếng Pháp", 2, "Ngoại ngữ"),
        SkillDefinition(16, "Tiếng Đức", 2, "Ngoại ngữ"),
        SkillDefinition(17, "Tiếng Tây Ban Nha", 2, "Ngoại ngữ"),
        SkillDefinition(18, "Guitar", 3, "Âm nhạc"),
        SkillDefinition(19, "Piano", 3, "Âm nhạc"),
        SkillDefinition(20, "Ukulele", 3, "Âm nhạc"),
        SkillDefinition(21, "Violin", 3, "Âm nhạc"),
        SkillDefinition(22, "Trống", 3, "Âm nhạc"),
        SkillDefinition(23, "Hát", 3, "Âm nhạc"),
        SkillDefinition(24, "Figma / UI Design", 4, "Thiết kế"),
        SkillDefinition(25, "Adobe Photoshop", 4, "Thiết kế"),
        SkillDefinition(26, "Illustrator", 4, "Thiết kế"),
        SkillDefinition(27, "Video Editing", 4, "Thiết kế"),
        SkillDefinition(28, "3D Modeling", 4, "Thiết kế"),
        SkillDefinition(29, "Motion Graphics", 4, "Thiết kế"),
        SkillDefinition(30, "Bóng đá", 6, "Thể thao"),
        SkillDefinition(31, "Cầu lông", 6, "Thể thao"),
        SkillDefinition(32, "Gym / Fitness", 6, "Thể thao"),
        SkillDefinition(33, "Yoga", 6, "Thể thao"),
        SkillDefinition(34, "Bơi lội", 6, "Thể thao"),
        SkillDefinition(35, "Cờ vua", 6, "Thể thao"),
        SkillDefinition(36, "Tennis", 6, "Thể thao"),
        SkillDefinition(37, "Marketing Digital", 7, "Kinh doanh"),
        SkillDefinition(38, "Excel nâng cao", 7, "Kinh doanh"),
        SkillDefinition(39, "Quản lý dự án", 7, "Kinh doanh"),
        SkillDefinition(40, "Beat making", 3, "Âm nhạc"),
        SkillDefinition(41, "Sáng tác nhạc", 3, "Âm nhạc")
    )
}
