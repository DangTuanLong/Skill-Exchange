package com.skillexchange.api.services.nlp

import com.skillexchange.api.config.EnvLoader
import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.nlp.ExtractedSkillDto
import io.ktor.client.HttpClient
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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Base64
import kotlin.math.abs

@Serializable
data class GeminiSkillRawItem(
    val name: String,
    val category: String? = "Khác",
    val type: String? = "HAVE",
    val proficiency_level: Int? = 2,
    val experience_years: Double? = 0.0,
    val confidence: Double? = 1.0,
    val evidence: String? = null
)

@Serializable
data class GeminiSkillRawResponse(
    val profile_summary: String? = null,
    val skills: List<GeminiSkillRawItem> = emptyList()
)

data class GeminiExtractionResult(
    val profileSummary: String?,
    val skills: List<ExtractedSkillDto>,
    val warning: String? = null
)

data class ResolvedSkillInfo(
    val id: Int,
    val name: String,
    val categoryId: Int,
    val categoryName: String
)

class GeminiSkillExtractionService(
    private val httpClient: HttpClient
) {
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

    private val systemPrompt = """
        Bạn là chuyên gia phân tích kỹ năng và đánh giá hồ sơ năng lực của SkillExchange.
        Nhiệm vụ của bạn là đọc kỹ tài liệu, hình ảnh chứng chỉ/bằng cấp/CV hoặc nội dung bio được cung cấp.
        Hãy trích xuất:
        1. Tất cả các kỹ năng chuyên môn, công nghệ, công cụ, ngoại ngữ, kỹ năng mềm được đề cập.
        2. Xác định rõ loại kỹ năng: 'HAVE' (kỹ năng ứng viên đã có, đã làm, có chứng chỉ) hoặc 'WANT' (kỹ năng ứng viên ghi rõ muốn học, trao đổi).
        3. Đánh giá mức độ thành thạo từ 1 đến 5 (1: Mới bắt đầu, 2: Cơ bản, 3: Khá, 4: Thành thạo, 5: Chuyên gia).
        4. Xác định số năm kinh nghiệm nếu có thông tin (nếu không nêu rõ, để 0.0).
        5. Tóm tắt ngắn gọn hồ sơ năng lực (profile_summary) không quá 3 câu bằng tiếng Việt.
        Trả về kết quả dưới dạng JSON tuân thủ đúng responseSchema.
    """.trimIndent()

    private fun buildGenerationConfig(): JsonObject {
        return buildJsonObject {
            put("responseMimeType", "application/json")
            put("responseSchema", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    put("profile_summary", buildJsonObject {
                        put("type", "STRING")
                        put("description", "Tóm tắt ngắn gọn năng lực chuyên môn hoặc giới thiệu bản thân từ nội dung cung cấp (tối đa 3 câu).")
                    })
                    put("skills", buildJsonObject {
                        put("type", "ARRAY")
                        put("description", "Danh sách các kỹ năng được trích xuất từ tài liệu, ảnh chứng chỉ/CV hoặc bio.")
                        put("items", buildJsonObject {
                            put("type", "OBJECT")
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject {
                                    put("type", "STRING")
                                    put("description", "Tên kỹ năng chuẩn hóa (ví dụ: Kotlin Android, Python, Thiết kế UI/UX, Tiếng Nhật, v.v.)")
                                })
                                put("category", buildJsonObject {
                                    put("type", "STRING")
                                    put("description", "Nhóm kỹ năng: Lập trình & Công nghệ, Ngoại ngữ, Thiết kế & Sáng tạo, Kinh doanh & Marketing, Kỹ năng mềm, Khác")
                                })
                                put("type", buildJsonObject {
                                    put("type", "STRING")
                                    put("enum", buildJsonArray {
                                        add("HAVE")
                                        add("WANT")
                                    })
                                    put("description", "HAVE nếu là kỹ năng người dùng đã có; WANT nếu là kỹ năng muốn học")
                                })
                                put("proficiency_level", buildJsonObject {
                                    put("type", "INTEGER")
                                    put("description", "Mức độ thành thạo từ 1 đến 5")
                                })
                                put("experience_years", buildJsonObject {
                                    put("type", "NUMBER")
                                    put("description", "Số năm kinh nghiệm nếu có đề cập (mặc định 0.0)")
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

    private fun buildTextRequestBody(text: String): JsonObject {
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "$systemPrompt\n\n[NỘI DUNG BIO / GIỚI THIỆU BẢN THÂN CỦA ỨNG VIÊN]:\n$text")
                        })
                    })
                })
            })
            put("generationConfig", buildGenerationConfig())
        }
    }

    private fun buildMultimodalRequestBody(fileBytes: ByteArray, mimeType: String, fileName: String): JsonObject {
        val base64Data = Base64.getEncoder().encodeToString(fileBytes)
        val normalizedMime = when {
            mimeType.startsWith("image/") -> mimeType
            mimeType == "application/pdf" -> "application/pdf"
            fileName.lowercase().endsWith(".pdf") -> "application/pdf"
            fileName.lowercase().endsWith(".png") -> "image/png"
            fileName.lowercase().endsWith(".jpg") || fileName.lowercase().endsWith(".jpeg") -> "image/jpeg"
            fileName.lowercase().endsWith(".webp") -> "image/webp"
            else -> "text/plain"
        }

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
                            put("text", "$systemPrompt\n\nTệp đính kèm: $fileName. Hãy phân tích toàn diện nội dung tệp này để trích xuất kỹ năng và tóm tắt hồ sơ.")
                        })
                    })
                })
            })
            put("generationConfig", buildGenerationConfig())
        }
    }

    private suspend fun callGeminiEndpoint(modelName: String, key: String, body: JsonObject): Result<String> = runCatching {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw RuntimeException("Gemini API error (${response.status.value}): $responseText")
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

        // 2. Nếu model chính lỗi và khác fallback, thử fallback
        val primaryError = primaryRes.exceptionOrNull()
        if (!primaryModel.equals(fallbackModel, ignoreCase = true)) {
            val fallbackRes = callGeminiEndpoint(fallbackModel, key, requestBody)
            if (fallbackRes.isSuccess) {
                return fallbackRes.getOrThrow()
            }
        }

        throw primaryError ?: RuntimeException("Gọi Gemini API thất bại")
    }

    private fun parseGeminiResponse(rawResponseJson: String): GeminiSkillRawResponse {
        val root = json.parseToJsonElement(rawResponseJson).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: throw IllegalStateException("Gemini không trả về kết quả (candidates rỗng)")
        if (candidates.isEmpty()) {
            throw IllegalStateException("Gemini không có ứng cử viên phản hồi")
        }
        val content = candidates[0].jsonObject["content"]?.jsonObject
            ?: throw IllegalStateException("Không tìm thấy content trong candidate")
        val parts = content["parts"]?.jsonArray
            ?: throw IllegalStateException("Không tìm thấy parts trong content")
        val textPart = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Không tìm thấy text trong parts")

        return json.decodeFromString<GeminiSkillRawResponse>(textPart)
    }

    private fun resolveSkillInDb(skillName: String, categoryName: String): ResolvedSkillInfo {
        return try {
            transaction {
                val existing = (SkillsTable innerJoin SkillCategoriesTable)
                    .selectAll()
                    .firstOrNull { it[SkillsTable.name].equals(skillName, ignoreCase = true) }

                if (existing != null) {
                    return@transaction ResolvedSkillInfo(
                        id = existing[SkillsTable.id],
                        name = existing[SkillsTable.name],
                        categoryId = existing[SkillsTable.categoryId],
                        categoryName = existing[SkillCategoriesTable.name]
                    )
                }

                val cat = SkillCategoriesTable.selectAll()
                    .firstOrNull { it[SkillCategoriesTable.name].equals(categoryName, ignoreCase = true) }
                    ?: SkillCategoriesTable.selectAll().firstOrNull()

                val catId = cat?.get(SkillCategoriesTable.id) ?: 1
                val catName = cat?.get(SkillCategoriesTable.name) ?: categoryName

                val newId = SkillsTable.insert {
                    it[name] = skillName
                    it[categoryId] = catId
                    it[description] = "Tạo tự động từ trích xuất AI Gemini"
                } get SkillsTable.id

                ResolvedSkillInfo(
                    id = newId,
                    name = skillName,
                    categoryId = catId,
                    categoryName = catName
                )
            }
        } catch (e: Exception) {
            ResolvedSkillInfo(
                id = abs(skillName.hashCode()).coerceAtLeast(1),
                name = skillName,
                categoryId = 1,
                categoryName = categoryName
            )
        }
    }

    private fun mapToExtractedSkills(raw: GeminiSkillRawResponse): GeminiExtractionResult {
        val skills = raw.skills.map { item ->
            val resolved = resolveSkillInDb(item.name.trim(), item.category ?: "Khác")
            ExtractedSkillDto(
                skillId = resolved.id,
                skillName = resolved.name,
                categoryId = resolved.categoryId,
                categoryName = resolved.categoryName,
                confidence = (item.confidence ?: 1.0).coerceIn(0.1, 1.0),
                matchType = "AI_GEMINI",
                matchedTerm = item.name,
                frequency = 1,
                score = (item.confidence ?: 1.0).coerceIn(0.1, 1.0),
                reason = item.evidence ?: "Phát hiện bởi Gemini AI",
                suggestedLevel = (item.proficiency_level ?: 2).coerceIn(1, 5),
                skillType = item.type ?: "HAVE",
                experienceYears = item.experience_years ?: 0.0,
                evidence = item.evidence
            )
        }
        return GeminiExtractionResult(
            profileSummary = raw.profile_summary,
            skills = skills
        )
    }

    suspend fun extractSkillsFromText(text: String): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val body = buildTextRequestBody(text)
        val rawResponseJson = callGeminiApiWithFallback(body)
        val rawData = parseGeminiResponse(rawResponseJson)
        mapToExtractedSkills(rawData)
    }

    suspend fun extractSkillsFromMultimodal(
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String
    ): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val body = buildMultimodalRequestBody(fileBytes, mimeType, fileName)
        val rawResponseJson = callGeminiApiWithFallback(body)
        val rawData = parseGeminiResponse(rawResponseJson)
        mapToExtractedSkills(rawData)
    }
}
