package com.skillexchange.api.services.nlp

import com.skillexchange.api.config.EnvLoader
import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.nlp.ExtractedSkillDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Serializable
data class AISkillRawItem(
    val name: String,
    val category: String? = "Khác",
    val type: String? = "HAVE",
    val proficiency_level: Int? = 2,
    val confidence: Double? = 1.0,
    val evidence: String? = null
)

@Serializable
data class AISkillRawResponse(
    val profile_summary: String? = null,
    val skills: List<AISkillRawItem> = emptyList()
)

data class AIExtractionResult(
    val profileSummary: String?,
    val skills: List<ExtractedSkillDto>,
    val warning: String? = null
)

// Giữ lại typealias để đảm bảo tương thích ngược nếu có module cũ gọi
typealias GeminiSkillRawItem = AISkillRawItem
typealias GeminiSkillRawResponse = AISkillRawResponse
typealias GeminiExtractionResult = AIExtractionResult

data class ResolvedSkillInfo(
    val id: Int,
    val name: String,
    val categoryId: Int,
    val categoryName: String
)

class AISkillExtractionService(
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(AISkillExtractionService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val primaryModel: String
        get() = EnvLoader.get("GEMINI_MODEL", "gemini-4-hydrogen").ifBlank { "gemini-4-hydrogen" }

    private val fallbackModel: String
        get() = "gemini-3.8-flash"

    private val apiKey: String
        get() = EnvLoader.get("GEMINI_API_KEY", "")

    // ─── IN-MEMORY TAXONOMY CACHE ─────────────────────────────────────────────
    // Lưu trữ nhóm kỹ năng -> danh sách tên kỹ năng, cùng bộ nhớ ánh xạ ID để phản hồi siêu tốc
    private val taxonomyCache = ConcurrentHashMap<String, MutableSet<String>>()
    private val categoryIdCache = ConcurrentHashMap<String, Int>()
    private val skillIdCache = ConcurrentHashMap<String, Int>()

    @Volatile
    private var isTaxonomyInitialized = false

    private fun ensureTaxonomyLoaded() {
        if (isTaxonomyInitialized) return
        synchronized(this) {
            if (isTaxonomyInitialized) return
            try {
                transaction {
                    val categories = SkillCategoriesTable.selectAll().toList()
                    for (cat in categories) {
                        val catId = cat[SkillCategoriesTable.id]
                        val catName = cat[SkillCategoriesTable.name]
                        categoryIdCache[catName.lowercase()] = catId
                        taxonomyCache.computeIfAbsent(catName) { ConcurrentHashMap.newKeySet() }
                    }

                    val skills = (SkillsTable innerJoin SkillCategoriesTable).selectAll().toList()
                    for (row in skills) {
                        val skillId = row[SkillsTable.id]
                        val skillName = row[SkillsTable.name]
                        val catName = row[SkillCategoriesTable.name]
                        skillIdCache[skillName.lowercase()] = skillId
                        taxonomyCache.computeIfAbsent(catName) { ConcurrentHashMap.newKeySet() }.add(skillName)
                    }
                }
                isTaxonomyInitialized = true
            } catch (_: Exception) {
                // Nếu DB tạm thời chưa sẵn sàng, sẽ thử lại ở lượt sau
            }
        }
    }

    private fun formatTaxonomyForPrompt(): String {
        ensureTaxonomyLoaded()
        if (taxonomyCache.isEmpty()) return ""
        val sb = StringBuilder("Danh mục nhóm kỹ năng và kỹ năng hiện có trong cơ sở dữ liệu hệ thống:\n")
        for ((catName, skills) in taxonomyCache) {
            sb.append("- Nhóm \"").append(catName).append("\": ")
            if (skills.isEmpty()) {
                sb.append("(chưa có kỹ năng cụ thể)")
            } else {
                sb.append(skills.joinToString(", "))
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(): String {
        val taxonomy = formatTaxonomyForPrompt()
        return """
            Bạn là chuyên gia phân tích kỹ năng và đánh giá hồ sơ năng lực của nền tảng SkillExchange.
            Nhiệm vụ của bạn là đọc kỹ tài liệu, hình ảnh chứng chỉ/bằng cấp/CV hoặc nội dung bio được cung cấp.

            $taxonomy

            Quy tắc trích xuất và phân loại bắt buộc:
            1. ƯU TIÊN CHUẨN HÓA VỚI DATABASE:
               - Nếu kỹ năng trong tài liệu/ảnh tương đương hoặc là biến thể của kỹ năng đã có trong danh mục trên (ví dụ: 'ReactJS', 'React Native' -> 'React'; 'Python 3', 'Python Programming' -> 'Python'), hãy CHUẨN HÓA về đúng tên kỹ năng và nhóm có sẵn trong danh mục.
            2. PHÁT HIỆN KỸ NĂNG MỚI:
               - Nếu người dùng có kỹ năng chưa xuất hiện trong danh mục trên:
                 + Đặt tên kỹ năng rõ ràng, chuẩn hóa (Title Case, ngắn gọn, ví dụ: 'Vận hành máy CNC', 'Thiết kế Vi mạch', 'Nuôi cấy mô', 'Sơ cứu & cấp cứu', 'Dịch vụ khách hàng').
                 + Xem xét kỹ năng đó có thuộc về nhóm kỹ năng nào đã có sẵn ở trên không. Nếu có, hãy dùng đúng tên nhóm đó.
                 + Nếu không thuộc bất kỳ nhóm nào đã có, hãy đề xuất một tên nhóm kỹ năng mới ngắn gọn, phù hợp (ví dụ: 'Cơ khí & Chế tạo', 'Hàng không & Vận tải', 'Y tế & Cấp cứu').
            3. Xác định rõ loại kỹ năng: 'HAVE' (kỹ năng ứng viên đã có, đã làm, có chứng chỉ) hoặc 'WANT' (kỹ năng ứng viên ghi rõ muốn học, tìm kiếm, trao đổi).
            4. Đánh giá mức độ thành thạo từ 1 đến 5 (1: Mới bắt đầu, 2: Cơ bản, 3: Khá, 4: Thành thạo, 5: Chuyên gia).
            5. Tóm tắt ngắn gọn hồ sơ năng lực (profile_summary) không quá 3 câu bằng tiếng Việt.

            Trả về kết quả dưới dạng JSON tuân thủ đúng responseSchema.
        """.trimIndent()
    }

    private fun buildGenerationConfig(): JsonObject {
        return buildJsonObject {
            put("responseMimeType", "application/json")
            put("responseSchema", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    put("profile_summary", buildJsonObject {
                        put("type", "STRING")
                        put(
                            "description",
                            "Tóm tắt ngắn gọn năng lực chuyên môn hoặc giới thiệu bản thân từ nội dung cung cấp (tối đa 3 câu)."
                        )
                    })
                    put("skills", buildJsonObject {
                        put("type", "ARRAY")
                        put(
                            "description",
                            "Danh sách các kỹ năng được trích xuất từ tài liệu, ảnh chứng chỉ/CV hoặc bio."
                        )
                        put("items", buildJsonObject {
                            put("type", "OBJECT")
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject {
                                    put("type", "STRING")
                                    put(
                                        "description",
                                        "Tên kỹ năng chuẩn hóa (ví dụ: Kotlin, Python, Thiết kế UI/UX, Tiếng Nhật, Sơ cứu & cấp cứu, v.v.)"
                                    )
                                })
                                put("category", buildJsonObject {
                                    put("type", "STRING")
                                    put(
                                        "description",
                                        "Nhóm kỹ năng tương ứng (ưu tiên nhóm có sẵn hoặc nhóm mới đề xuất)"
                                    )
                                })
                                put("type", buildJsonObject {
                                    put("type", "STRING")
                                    put("enum", buildJsonArray {
                                        add("HAVE")
                                        add("WANT")
                                    })
                                    put(
                                        "description",
                                        "HAVE nếu là kỹ năng người dùng đã có; WANT nếu là kỹ năng muốn học"
                                    )
                                })
                                put("proficiency_level", buildJsonObject {
                                    put("type", "INTEGER")
                                    put("description", "Mức độ thành thạo từ 1 đến 5")
                                })
                                put("confidence", buildJsonObject {
                                    put("type", "NUMBER")
                                    put("description", "Độ tin cậy trích xuất từ 0.0 đến 1.0")
                                })
                                put("evidence", buildJsonObject {
                                    put("type", "STRING")
                                    put("description", "Bằng chứng hoặc từ khóa trong tài liệu cho kỹ năng này")
                                })
                            })
                            put("required", buildJsonArray {
                                add("name")
                                add("category")
                                add("type")
                                add("proficiency_level")
                                add("confidence")
                            })
                        })
                    })
                })
                put("required", buildJsonArray {
                    add("skills")
                })
            })
        }
    }

    private fun detectMimeType(fileBytes: ByteArray, mimeType: String, fileName: String): String {
        // Kiểm tra magic bytes nhận diện loại file thực tế
        if (fileBytes.size >= 3 && fileBytes[0] == 0xFF.toByte() && fileBytes[1] == 0xD8.toByte() && fileBytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (fileBytes.size >= 8 && fileBytes[0] == 0x89.toByte() && fileBytes[1] == 0x50.toByte() && fileBytes[2] == 0x4E.toByte() && fileBytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        if (fileBytes.size >= 4 && fileBytes[0] == 0x25.toByte() && fileBytes[1] == 0x50.toByte() && fileBytes[2] == 0x44.toByte() && fileBytes[3] == 0x46.toByte()) {
            return "application/pdf"
        }
        if (fileBytes.size >= 12 && fileBytes[0] == 0x52.toByte() && fileBytes[1] == 0x49.toByte() && fileBytes[2] == 0x46.toByte() && fileBytes[3] == 0x46.toByte() &&
            fileBytes[8] == 0x57.toByte() && fileBytes[9] == 0x45.toByte() && fileBytes[10] == 0x42.toByte() && fileBytes[11] == 0x50.toByte()) {
            return "image/webp"
        }

        if (mimeType.startsWith("image/") || mimeType == "application/pdf" || mimeType == "text/plain") {
            return mimeType
        }

        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") || lower.endsWith(".md") -> "text/plain"
            else -> "image/jpeg"
        }
    }

    private fun buildTextRequestBody(text: String): JsonObject {
        val prompt = buildSystemPrompt()
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "$prompt\n\n[NỘI DUNG BIO / GIỚI THIỆU BẢN THÂN CỦA ỨNG VIÊN]:\n$text")
                        })
                    })
                })
            })
            put("generationConfig", buildGenerationConfig())
        }
    }

    private fun buildMultimodalRequestBody(fileBytes: ByteArray, mimeType: String, fileName: String): JsonObject {
        val base64Data = Base64.getEncoder().encodeToString(fileBytes)
        val normalizedMime = detectMimeType(fileBytes, mimeType, fileName)

        val prompt = buildSystemPrompt()
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", normalizedMime)
                                put("data", base64Data)
                            })
                        })
                        add(buildJsonObject {
                            put(
                                "text",
                                "$prompt\n\n[TÊN TỆP TẢI LÊN]: $fileName\nHãy phân tích kỹ lưỡng nội dung tệp trên để trích xuất hồ sơ năng lực và danh sách kỹ năng."
                            )
                        })
                    })
                })
            })
            put("generationConfig", buildGenerationConfig())
        }
    }

    private suspend fun callGeminiEndpoint(
        model: String,
        apiKey: String,
        requestBody: JsonObject
    ): Result<String> = runCatching {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            header("x-goog-api-key", apiKey)
            timeout {
                requestTimeoutMillis = 60_000L
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis = 60_000L
            }
            setBody(requestBody.toString())
        }

        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw RuntimeException("AI Model [$model] trả về HTTP ${response.status.value}: $responseText")
        }
        responseText
    }

    private suspend fun callGeminiApiWithFallback(requestBody: JsonObject): String {
        val key = apiKey
        if (key.isBlank()) {
            throw IllegalStateException("Chưa cấu hình GEMINI_API_KEY trong file .env hoặc biến môi trường.")
        }

        // 1. Thử gọi model chính
        val primaryRes = callGeminiEndpoint(primaryModel, key, requestBody)
        if (primaryRes.isSuccess) {
            return primaryRes.getOrThrow()
        }

        // 2. Nếu model chính lỗi và khác fallback, thử fallback sang gemini-3.8-flash
        val primaryError = primaryRes.exceptionOrNull()
        logger.warn("AI Model $primaryModel thất bại: ${primaryError?.message}. Thử fallback sang $fallbackModel")
        if (!primaryModel.equals(fallbackModel, ignoreCase = true)) {
            val fallbackRes = callGeminiEndpoint(fallbackModel, key, requestBody)
            if (fallbackRes.isSuccess) {
                return fallbackRes.getOrThrow()
            }
        }

        throw primaryError ?: RuntimeException("Gọi AI Service thất bại")
    }

    private fun cleanJsonText(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val lines = trimmed.lines()
            if (lines.size >= 2) {
                val startIdx = if (lines.first().startsWith("```")) 1 else 0
                val endIdx = if (lines.last().trim() == "```") lines.size - 1 else lines.size
                trimmed = lines.subList(startIdx, endIdx).joinToString("\n").trim()
            }
        }
        return trimmed
    }

    private fun parseAIResponse(rawResponseJson: String): AISkillRawResponse {
        val root = json.parseToJsonElement(rawResponseJson).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: throw IllegalStateException("AI không trả về kết quả (candidates rỗng)")
        if (candidates.isEmpty()) {
            throw IllegalStateException("AI không có ứng cử viên phản hồi")
        }
        val content = candidates[0].jsonObject["content"]?.jsonObject
            ?: throw IllegalStateException("Không tìm thấy content trong candidate")
        val parts = content["parts"]?.jsonArray
            ?: throw IllegalStateException("Không tìm thấy parts trong content")
        val textPart = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Không tìm thấy text trong parts")

        val cleaned = cleanJsonText(textPart)
        return json.decodeFromString<AISkillRawResponse>(cleaned)
    }

    private fun resolveSkillInDb(rawSkillName: String, rawCategoryName: String): ResolvedSkillInfo {
        val skillName = rawSkillName.trim()
        val categoryName = rawCategoryName.trim().ifBlank { "Khác" }

        ensureTaxonomyLoaded()

        return try {
            transaction {
                // 1. Kiểm tra kỹ năng đã tồn tại chưa (case-insensitive)
                val existing = (SkillsTable innerJoin SkillCategoriesTable)
                    .selectAll()
                    .where { SkillsTable.name.lowerCase() eq skillName.lowercase() }
                    .firstOrNull()

                if (existing != null) {
                    val sId = existing[SkillsTable.id]
                    val sName = existing[SkillsTable.name]
                    val cId = existing[SkillsTable.categoryId]
                    val cName = existing[SkillCategoriesTable.name]

                    // Đồng bộ RAM cache
                    skillIdCache[sName.lowercase()] = sId
                    categoryIdCache[cName.lowercase()] = cId
                    taxonomyCache.computeIfAbsent(cName) { ConcurrentHashMap.newKeySet() }.add(sName)

                    return@transaction ResolvedSkillInfo(
                        id = sId,
                        name = sName,
                        categoryId = cId,
                        categoryName = cName
                    )
                }

                // 2. Kỹ năng chưa có trong DB: Kiểm tra xem category đã tồn tại chưa
                val existingCat = SkillCategoriesTable.selectAll()
                    .where { SkillCategoriesTable.name.lowerCase() eq categoryName.lowercase() }
                    .firstOrNull()

                val (catId, finalCatName) = if (existingCat != null) {
                    existingCat[SkillCategoriesTable.id] to existingCat[SkillCategoriesTable.name]
                } else {
                    // Tạo nhóm kỹ năng mới trong cơ sở dữ liệu
                    val newCatId = SkillCategoriesTable.insert {
                        it[name] = categoryName
                        it[icon] = "category"
                        it[description] = "Tạo tự động từ trích xuất kỹ năng AI"
                    } get SkillCategoriesTable.id
                    categoryIdCache[categoryName.lowercase()] = newCatId
                    taxonomyCache.computeIfAbsent(categoryName) { ConcurrentHashMap.newKeySet() }
                    newCatId to categoryName
                }

                // 3. Tạo kỹ năng mới gắn với categoryId
                val newSkillId = SkillsTable.insert {
                    it[name] = skillName
                    it[categoryId] = catId
                    it[description] = "Tạo tự động từ trích xuất AI"
                } get SkillsTable.id

                // Cập nhật ngay vào RAM cache để các request kế tiếp AI tự động có kỹ năng này
                skillIdCache[skillName.lowercase()] = newSkillId
                categoryIdCache[finalCatName.lowercase()] = catId
                taxonomyCache.computeIfAbsent(finalCatName) { ConcurrentHashMap.newKeySet() }.add(skillName)

                ResolvedSkillInfo(
                    id = newSkillId,
                    name = skillName,
                    categoryId = catId,
                    categoryName = finalCatName
                )
            }
        } catch (_: Exception) {
            ResolvedSkillInfo(
                id = abs(skillName.hashCode()).coerceAtLeast(1),
                name = skillName,
                categoryId = 1,
                categoryName = categoryName
            )
        }
    }

    private fun mapToExtractedSkills(raw: AISkillRawResponse): AIExtractionResult {
        val skills = raw.skills.map { item ->
            val resolved = resolveSkillInDb(item.name.trim(), item.category ?: "Khác")
            ExtractedSkillDto(
                skillId = resolved.id,
                skillName = resolved.name,
                categoryId = resolved.categoryId,
                categoryName = resolved.categoryName,
                confidence = (item.confidence ?: 1.0).coerceIn(0.1, 1.0),
                matchType = "AI_EXTRACTION",
                matchedTerm = item.name,
                frequency = 1,
                score = (item.confidence ?: 1.0).coerceIn(0.1, 1.0),
                reason = item.evidence ?: "Phát hiện bởi AI",
                suggestedLevel = (item.proficiency_level ?: 2).coerceIn(1, 5),
                skillType = item.type ?: "HAVE",
                experienceYears = 0.0,
                evidence = item.evidence
            )
        }
        return AIExtractionResult(
            profileSummary = raw.profile_summary,
            skills = skills
        )
    }

    suspend fun extractSkillsFromText(text: String): AIExtractionResult = withContext(Dispatchers.IO) {
        val body = buildTextRequestBody(text)
        val rawResponseJson = callGeminiApiWithFallback(body)
        val rawData = parseAIResponse(rawResponseJson)
        mapToExtractedSkills(rawData)
    }

    suspend fun extractSkillsFromMultimodal(
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String
    ): AIExtractionResult = withContext(Dispatchers.IO) {
        val body = buildMultimodalRequestBody(fileBytes, mimeType, fileName)
        val rawResponseJson = callGeminiApiWithFallback(body)
        val rawData = parseAIResponse(rawResponseJson)
        mapToExtractedSkills(rawData)
    }
}
