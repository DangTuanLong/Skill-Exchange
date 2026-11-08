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
     * Chuẩn hóa văn bản tài liệu (Bước a - Text Normalization):
     * 1. Chuẩn hóa Unicode NFC.
     * 2. Chuẩn hóa khoảng trắng Unicode (NBSP, zero-width space, tabs, en/em space).
     * 3. Gộp các từ bị ngắt dòng có gạch nối (hyphenation): (\p{L}+)-\s*\r?\n\s*(\p{L}+) -> $1$2.
     * 4. Chuẩn hóa các ký hiệu đầu dòng bullet points (•, ▪, ▫, –, —, *, +) thành '- '.
     */
    fun normalizeDocumentText(text: String): String {
        var normalized = normalizeNfc(text)
        normalized = normalized.replace(Regex("[\u00A0\u200B\u2000-\u200A\uFEFF]"), " ")
        normalized = normalized.replace(Regex("(\\p{L}+)-\\s*\\r?\\n\\s*(\\p{L}+)"), "$1$2")
        normalized = normalized.replace(Regex("^[ \t]*[•▪▫–—*+][ \t]*", RegexOption.MULTILINE), "- ")
        return normalized
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

    enum class MatchIntent {
        HAVE,
        WANT,
        NEGATIVE
    }

    enum class CvSectionType {
        SKILLS,
        EXPERIENCE,
        EDUCATION,
        OBJECTIVE_WANT,
        PROJECTS,
        OTHER
    }

    data class TextSection(
        val type: CvSectionType,
        val startOffset: Int,
        val endOffset: Int,
        val rawHeader: String
    )

    companion object {
        private val CLAUSE_DELIMITERS = charArrayOf(',', ';', '.', '!', '?', '\n', '\r', '—', '–')

        private val SECTION_HEADER_PATTERNS = listOf(
            CvSectionType.OBJECTIVE_WANT to Regex("(?im)^[ \t]*[-#*•\\[\\]]*[ \t]*(?:muc\\s+tieu(?:\\s+nghe\\s+nghiep|\\s+ca\\s+nhan)?|dinh\\s+huong|nhu\\s+cau\\s+(?:trao\\s+doi\\s+&\\s+)?hoc\\s+tap|nhu\\s+cau\\s+hoc\\s+hoi\\s+them|ky\\s+nang\\s+muon\\s+hoc|career\\s+objective|objective|goals|want\\s+to\\s+learn)[^\n:]*[:\n]"),
            CvSectionType.SKILLS to Regex("(?im)^[ \t]*[-#*•\\[\\]]*[ \t]*(?:ky\\s+nang(?:\\s+chuyen\\s+mon|\\s+chinh|\\s+ca\\s+nhan|\\s+hien\\s+co)?|chuyen\\s+mon|nang\\s+luc\\s+chuyen\\s+mon|skills(?:\\s+&\\s+technologies|\\s+&\\s+tools)?|technical\\s+skills|core\\s+competencies)[^\n:]*[:\n]"),
            CvSectionType.EXPERIENCE to Regex("(?im)^[ \t]*[-#*•\\[\\]]*[ \t]*(?:kinh\\s+nghiem(?:\\s+lam\\s+viec|\\s+nghe\\s+nghiep)?|lich\\s+su\\s+lam\\s+viec|work\\s+experience|professional\\s+experience|experience)[^\n:]*[:\n]"),
            CvSectionType.EDUCATION to Regex("(?im)^[ \t]*[-#*•\\[\\]]*[ \t]*(?:hoc\\s+van|trinh\\s+do|bang\\s+cap|education|academic\\s+background)[^\n:]*[:\n]"),
            CvSectionType.PROJECTS to Regex("(?im)^[ \t]*[-#*•\\[\\]]*[ \t]*(?:du\\s+an(?:\\s+tieu\\s+bieu|\\s+ca\\s+nhan)?|projects|personal\\s+projects)[^\n:]*[:\n]")
        )

        // 5 cụm danh từ tổ chức cố định (Bước d - Organization Context Guard)
        private val ORG_PREFIXES = listOf(
            Regex("\\b(?:truong\\s+)?(?:dai\\s+hoc|cao\\s+dang)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:hoc\\s+vien|vien\\s+nghien\\s+cuu|vien\\s+khoa\\s+hoc|vien\\s+han\\s+lam|trung\\s+tam)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:cong\\s+ty|cty|tap\\s+doan|doanh\\s+nghiep)(?:\\s+(?:tnhh|co\\s+phan|cp))?\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:cau\\s+lac\\s+bo|clb)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:truong(?:\\s+thpt|\\s+thcs|\\s+tieu\\s+hoc)?|benh\\s+vien)\\b", RegexOption.IGNORE_CASE)
        )

        // Các động từ / ranh giới ngắt cụm tổ chức (Ví dụ: "Học viện X dạy Python")
        private val ACTION_VERBS_DELIMITERS = Regex(
            "\\b(?:day|hoc|tuyen|tuyen\\s+dung|dao\\s+tao|nghien\\s+cuu|phat\\s+trien|su\\s+dung|lam\\s+viec\\s+voi)\\b",
            RegexOption.IGNORE_CASE
        )

        private val NEGATIVE_PATTERNS = listOf(
            // Tiếng Việt (unaccented)
            Regex("\\b(?:chua\\s+tung|chua\\s+co\\s+kinh\\s+nghiem|khong\\s+co\\s+kinh\\s+nghiem|khong\\s+co\\s+chuyen\\s+mon|khong\\s+chuyen|khong\\s+biet|chua\\s+biet|hoan\\s+toan\\s+khong|khong\\s+thao|yeu\\s+ve|mat\\s+goc|chua|khong|kem)\\b", RegexOption.IGNORE_CASE),
            // Tiếng Anh
            Regex("\\b(?:no\\s+hands-on\\s+experience\\s+in|no\\s+experience\\s+with|no\\s+experience\\s+in|have\\s+no\\s+experience|not\\s+familiar\\s+with|not\\s+good\\s+at|cannot|can't|haven't|never|not|no)\\b", RegexOption.IGNORE_CASE)
        )

        private val AFFIRMATIVE_EXCEPTIONS = listOf(
            Regex("\\bkhong\\s+nhung\\b.*?\\bma\\s+con\\b", RegexOption.IGNORE_CASE),
            Regex("\\bkhong\\s+chi\\b.*?\\bma\\s+con\\b", RegexOption.IGNORE_CASE),
            Regex("\\bkhong\\s+chi\\b.*?\\bnhung\\s+con\\b", RegexOption.IGNORE_CASE)
        )

        private val FALSE_NEGATIVE_WORDS = listOf(
            Regex("\\bkhong\\s+gian\\b", RegexOption.IGNORE_CASE),
            Regex("\\bkhong\\s+dau\\b", RegexOption.IGNORE_CASE),
            Regex("\\bkhong\\s+khi\\b", RegexOption.IGNORE_CASE)
        )

        private val WANT_PATTERNS = listOf(
            // Tiếng Việt (unaccented)
            Regex("\\b(?:muon\\s+hoc|can\\s+hoc|can\\s+tim\\s+nguoi\\s+(?:huong\\s+dan|day)|dang\\s+tim\\s+nguoi\\s+(?:huong\\s+dan|day)|dang\\s+rat\\s+can\\s+tim\\s+nguoi|du\\s+dinh\\s+hoc|ke\\s+hoach\\s+hoc|ke\\s+hoach\\s+(?:nam\\s+toi|sap\\s+toi)?\\s*:\\s*muon\\s+hoc|muon\\s+trau\\s+doi|nhu\\s+cau\\s+(?:trao\\s+doi\\s+&\\s+)?hoc\\s+tap|tim\\s+nguoi\\s+huong\\s+dan\\s+hoc)\\b", RegexOption.IGNORE_CASE),
            // Tiếng Anh
            Regex("\\b(?:want\\s+to\\s+learn|planning\\s+to\\s+learn|looking\\s+to\\s+learn|looking\\s+for\\s+(?:a\\s+)?(?:tutor|mentor)\\s+to\\s+learn|wish\\s+to\\s+learn|interested\\s+in\\s+learning)\\b", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Phân tách các phần mục của CV (Bước b - Section-aware Parsing).
     */
    fun parseCvSections(text: String): List<TextSection> {
        val detected = mutableListOf<Pair<Int, Pair<CvSectionType, String>>>()
        for ((type, regex) in SECTION_HEADER_PATTERNS) {
            val matches = regex.findAll(text)
            for (m in matches) {
                detected.add(m.range.first to (type to m.value))
            }
        }
        detected.sortBy { it.first }
        if (detected.isEmpty()) return emptyList()

        val sections = mutableListOf<TextSection>()
        for (i in detected.indices) {
            val (start, pair) = detected[i]
            val (type, header) = pair
            val end = if (i + 1 < detected.size) detected[i + 1].first else text.length
            sections.add(TextSection(type, start, end, header))
        }
        return sections
    }

    fun getSectionTypeAt(sections: List<TextSection>, offset: Int): CvSectionType {
        for (sec in sections) {
            if (offset in sec.startOffset until sec.endOffset) {
                return sec.type
            }
        }
        return CvSectionType.OTHER
    }

    /**
     * Kiểm tra xem vị trí kỹ năng có nằm trọn trong cụm danh xưng tổ chức không (Bước d):
     * - Khống chế cửa sổ tối đa 3-4 từ sau danh xưng tổ chức.
     * - Dừng ở dấu câu ngắt mệnh đề.
     * - Không chặn nếu có động từ hành động ở giữa (ví dụ: "Học viện X dạy Python").
     */
    fun isInsideOrganizationName(text: String, matchStartIndex: Int): Boolean {
        val windowStart = maxOf(0, matchStartIndex - 60)
        val rawWindow = text.substring(windowStart, matchStartIndex)

        val lastDelimiterIndex = rawWindow.lastIndexOfAny(CLAUSE_DELIMITERS)
        val window = if (lastDelimiterIndex != -1) {
            rawWindow.substring(lastDelimiterIndex + 1)
        } else {
            rawWindow
        }.trim()

        if (window.isBlank()) return false

        for (orgRegex in ORG_PREFIXES) {
            val orgMatch = orgRegex.findAll(window).lastOrNull()
            if (orgMatch != null) {
                val between = window.substring(orgMatch.range.last + 1).trim()
                val wordCount = if (between.isEmpty()) 0 else between.split("\\s+".toRegex()).size
                if (wordCount <= 3) {
                    // Nếu giữa danh xưng tổ chức và kỹ năng có động từ hành động -> không chặn!
                    if (ACTION_VERBS_DELIMITERS.containsMatchIn(between)) {
                        return false
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * Gợi ý mức độ thành thạo và lý do từ ngữ cảnh (Bước f - Proficiency Inference):
     * Chỉ ở dạng đề xuất tham khảo, mức mặc định vẫn là 2/5, người dùng có toàn quyền điều chỉnh.
     */
    fun inferSkillProficiency(text: String, matchIndices: List<Int>): Pair<Int, String> {
        for (idx in matchIndices) {
            val start = maxOf(0, idx - 80)
            val end = minOf(text.length, idx + 80)
            val window = text.substring(start, end).lowercase()

            if (Regex("\\b(?:lead|principal|architect|expert|chuyen\\s+gia|[5-9]\\s*nam|1[0-9]\\s*nam)\\b").containsMatchIn(window)) {
                return 5 to "Phát hiện thâm niên chuyên gia / ≥ 5 năm"
            }
            if (Regex("\\b(?:senior|thanh\\s+thao|luu\\s+loat|advanced|[3-4]\\s*nam)\\b").containsMatchIn(window)) {
                return 4 to "Phát hiện thâm niên Senior / 3-5 năm"
            }
            if (Regex("\\b(?:mid|mid-level|intermediate|[1-2]\\s*nam)\\b").containsMatchIn(window)) {
                return 3 to "Phát hiện kinh nghiệm 1-2 năm"
            }
            if (Regex("\\b(?:junior|fresher|co\\s+ban|basic)\\b").containsMatchIn(window)) {
                return 2 to "Phát hiện mức cơ bản / Junior"
            }
            if (Regex("\\b(?:moi\\s+hoc|nhap\\s+mon|beginner)\\b").containsMatchIn(window)) {
                return 1 to "Phát hiện mới tiếp cận / mới bắt đầu"
            }
        }
        return 2 to "Mức đề xuất mặc định (2/5)"
    }

    /**
     * Xác định ý định (HAVE / WANT / NEGATIVE) tại vị trí xuất hiện của kỹ năng:
     * - Kiểm tra xem có nằm trong mục OBJECTIVE_WANT không.
     * - Cửa sổ ngắn tối đa 60 ký tự trước vị trí khớp.
     * - Dừng ngay khi gặp dấu câu ngắt mệnh đề gần nhất.
     * - Kiểm tra ngoại lệ khẳng định (không những... mà còn).
     * - Loại trừ các từ ghép không mang nghĩa phủ định (không gian, không dấu, không khí).
     */
    fun determineIntentAt(
        text: String,
        matchStartIndex: Int,
        sections: List<TextSection> = emptyList()
    ): MatchIntent {
        // Nếu nằm trong mục Mục tiêu / Nhu cầu học hỏi (WANT section)
        if (getSectionTypeAt(sections, matchStartIndex) == CvSectionType.OBJECTIVE_WANT) {
            return MatchIntent.WANT
        }

        val windowStart = maxOf(0, matchStartIndex - 60)
        val rawWindow = text.substring(windowStart, matchStartIndex)

        val lastDelimiterIndex = rawWindow.lastIndexOfAny(CLAUSE_DELIMITERS)
        val window = if (lastDelimiterIndex != -1) {
            rawWindow.substring(lastDelimiterIndex + 1)
        } else {
            rawWindow
        }.trim()

        if (window.isBlank()) return MatchIntent.HAVE

        for (pattern in AFFIRMATIVE_EXCEPTIONS) {
            if (pattern.containsMatchIn(window)) {
                return MatchIntent.HAVE
            }
        }

        var cleanWindow = window
        for (falseNeg in FALSE_NEGATIVE_WORDS) {
            cleanWindow = cleanWindow.replace(falseNeg, "")
        }

        for (pattern in NEGATIVE_PATTERNS) {
            if (pattern.containsMatchIn(cleanWindow)) {
                return MatchIntent.NEGATIVE
            }
        }

        for (pattern in WANT_PATTERNS) {
            if (pattern.containsMatchIn(window)) {
                return MatchIntent.WANT
            }
        }

        return MatchIntent.HAVE
    }

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
        val nfcText = normalizeDocumentText(rawText)
        val unaccentedBio = unaccent(nfcText)
        val sections = parseCvSections(unaccentedBio)
        val skills = getSkillDefinitions()

        // Map kết quả tạm: skillId -> ExtractedSkillDto
        val resultMap = mutableMapOf<Int, ExtractedSkillDto>()

        // 1. TẦNG 1: EXACT MATCH TÊN KỸ NĂNG CHUẨN TRONG DB (Confidence: 1.0)
        for (skill in skills) {
            val unaccentedName = unaccent(skill.name)
            if (unaccentedName.isBlank()) continue

            // Ngoại lệ từ đồng âm do bỏ dấu (Homograph collision với giới từ thông dụng):
            // "Trống" sau khi unaccent thành "trong" trùng giới từ tiếng Việt "trong".
            // Để exact match "Trống", văn bản có dấu phải thực sự chứa "trống"/"Trống".
            // Nếu là văn bản không dấu, "trống" phải đi qua Alias match ("danh trong", "choi trong", "bo trong", "trong jazz").
            if (skill.name == "Trống") {
                val accentedRegex = Regex("(?<![\\p{L}\\p{N}])[Tt]rống(?![\\p{L}\\p{N}])")
                val matches = accentedRegex.findAll(nfcText).toList()
                if (matches.isNotEmpty()) {
                    val hasHaveMatch = matches.any { match ->
                        !isInsideOrganizationName(unaccentedBio, match.range.first) &&
                            determineIntentAt(unaccentedBio, match.range.first, sections) == MatchIntent.HAVE
                    }
                    if (hasHaveMatch) {
                        val frequency = matches.size
                        val inSkillsSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.SKILLS }
                        val inExpSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.EXPERIENCE }
                        val sectionWeight = if (inSkillsSection) 1.05 else if (inExpSection) 1.0 else 0.95
                        val score = 1.0 * (1.0 + 0.05 * minOf(frequency - 1, 3)) * sectionWeight
                        val (sLevel, sReason) = inferSkillProficiency(unaccentedBio, matches.map { it.range.first })

                        val existing = resultMap[skill.id]
                        if (existing == null || existing.confidence < 1.0) {
                            resultMap[skill.id] = ExtractedSkillDto(
                                skillId = skill.id,
                                skillName = skill.name,
                                categoryId = skill.categoryId,
                                categoryName = skill.categoryName,
                                confidence = 1.0,
                                matchType = "EXACT",
                                matchedTerm = skill.name,
                                frequency = frequency,
                                score = score,
                                reason = sReason,
                                suggestedLevel = sLevel
                            )
                        }
                    }
                }
                continue
            }

            val regex = buildWordBoundaryRegex(unaccentedName)
            val matches = regex.findAll(unaccentedBio).toList()
            if (matches.isNotEmpty()) {
                val hasHaveMatch = matches.any { match ->
                    !isInsideOrganizationName(unaccentedBio, match.range.first) &&
                        determineIntentAt(unaccentedBio, match.range.first, sections) == MatchIntent.HAVE
                }
                if (hasHaveMatch) {
                    val frequency = matches.size
                    val inSkillsSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.SKILLS }
                    val inExpSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.EXPERIENCE }
                    val sectionWeight = if (inSkillsSection) 1.05 else if (inExpSection) 1.0 else 0.95
                    val score = 1.0 * (1.0 + 0.05 * minOf(frequency - 1, 3)) * sectionWeight
                    val (sLevel, sReason) = inferSkillProficiency(unaccentedBio, matches.map { it.range.first })

                    val existing = resultMap[skill.id]
                    if (existing == null || existing.confidence < 1.0) {
                        resultMap[skill.id] = ExtractedSkillDto(
                            skillId = skill.id,
                            skillName = skill.name,
                            categoryId = skill.categoryId,
                            categoryName = skill.categoryName,
                            confidence = 1.0,
                            matchType = "EXACT",
                            matchedTerm = skill.name,
                            frequency = frequency,
                            score = score,
                            reason = sReason,
                            suggestedLevel = sLevel
                        )
                    }
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
                val matches = regex.findAll(unaccentedBio).toList()
                if (matches.isNotEmpty()) {
                    val hasHaveMatch = matches.any { match ->
                        !isInsideOrganizationName(unaccentedBio, match.range.first) &&
                            determineIntentAt(unaccentedBio, match.range.first, sections) == MatchIntent.HAVE
                    }
                    if (hasHaveMatch) {
                        val frequency = matches.size
                        val inSkillsSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.SKILLS }
                        val inExpSection = matches.any { getSectionTypeAt(sections, it.range.first) == CvSectionType.EXPERIENCE }
                        val sectionWeight = if (inSkillsSection) 1.05 else if (inExpSection) 1.0 else 0.95
                        val score = 0.9 * (1.0 + 0.05 * minOf(frequency - 1, 3)) * sectionWeight
                        val (sLevel, sReason) = inferSkillProficiency(unaccentedBio, matches.map { it.range.first })

                        val existing = resultMap[skill.id]
                        if (existing == null || existing.confidence < 0.9) {
                            resultMap[skill.id] = ExtractedSkillDto(
                                skillId = skill.id,
                                skillName = skill.name,
                                categoryId = skill.categoryId,
                                categoryName = skill.categoryName,
                                confidence = 0.9,
                                matchType = "ALIAS",
                                matchedTerm = alias,
                                frequency = frequency,
                                score = score,
                                reason = sReason,
                                suggestedLevel = sLevel
                            )
                        }
                        break
                    }
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
                        val tokenIndex = unaccentedBio.indexOf(token)
                        val isHave = if (tokenIndex != -1) {
                            !isInsideOrganizationName(unaccentedBio, tokenIndex) &&
                                determineIntentAt(unaccentedBio, tokenIndex, sections) == MatchIntent.HAVE
                        } else true

                        if (isHave) {
                            val (sLevel, sReason) = inferSkillProficiency(unaccentedBio, listOf(tokenIndex.coerceAtLeast(0)))
                            resultMap[skill.id] = ExtractedSkillDto(
                                skillId = skill.id,
                                skillName = skill.name,
                                categoryId = skill.categoryId,
                                categoryName = skill.categoryName,
                                confidence = 0.75,
                                matchType = "FUZZY",
                                matchedTerm = "$token ~ $targetWord",
                                frequency = 1,
                                score = 0.75,
                                reason = sReason,
                                suggestedLevel = sLevel
                            )
                        }
                    }
                }
            }
        }

        // Sắp xếp kết quả theo danh mục (Bước e), trong mỗi danh mục theo score giảm dần, sau đó theo ID tăng dần
        return resultMap.values
            .sortedWith(
                compareBy<ExtractedSkillDto> { it.categoryId ?: 999 }
                    .thenByDescending { it.score }
                    .thenBy { it.skillId }
            )
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
