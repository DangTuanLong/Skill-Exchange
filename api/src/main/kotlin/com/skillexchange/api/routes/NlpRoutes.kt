package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsDocumentRequest
import com.skillexchange.api.models.nlp.ExtractSkillsRequest
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.nlp.DocumentRateLimiter
import com.skillexchange.api.services.nlp.GeminiExtractionResult
import com.skillexchange.api.services.nlp.GeminiSkillExtractionService
import com.skillexchange.api.services.nlp.NlpRateLimiter
import com.skillexchange.api.services.nlp.SkillExtractionService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore

class DocumentParseException(
    val statusCode: HttpStatusCode,
    val errorCode: String,
    override val message: String
) : Exception(message)

fun Route.nlpRoutes(
    skillExtractionService: SkillExtractionService,
    rateLimiter: NlpRateLimiter = NlpRateLimiter(),
    documentRateLimiter: DocumentRateLimiter = DocumentRateLimiter(),
    documentConcurrencyLimiter: Semaphore = Semaphore(5)
) {
    nlpRoutes(
        skillExtractionService = skillExtractionService,
        geminiService = null,
        rateLimiter = rateLimiter,
        documentRateLimiter = documentRateLimiter,
        documentConcurrencyLimiter = documentConcurrencyLimiter
    )
}

fun Route.nlpRoutes(
    skillExtractionService: SkillExtractionService,
    geminiService: GeminiSkillExtractionService?,
    rateLimiter: NlpRateLimiter = NlpRateLimiter(),
    documentRateLimiter: DocumentRateLimiter = DocumentRateLimiter(),
    documentConcurrencyLimiter: Semaphore = Semaphore(5)
) {
    route("/api/nlp") {
        authenticate("auth-jwt") {
            post("/extract-skills") {
                val userId = try {
                    call.getUserId()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            status = 401,
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Chưa xác thực hoặc token không hợp lệ"
                        )
                    )
                }

                // Kiểm tra rate limiting: 20 requests/phút/user
                if (!rateLimiter.tryAcquire(userId)) {
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đã gửi quá nhiều yêu cầu gợi ý kỹ năng. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val req = try {
                    call.receive<ExtractSkillsRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                if (req.text.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Nội dung bio không được để trống",
                            fieldErrors = mapOf("text" to "Nội dung bio không được để trống")
                        )
                    )
                }

                if (req.text.length > 500) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Nội dung bio vượt quá giới hạn 500 ký tự",
                            fieldErrors = mapOf("text" to "Độ dài tối đa là 500 ký tự")
                        )
                    )
                }

                val extractionResult = if (geminiService != null) {
                    try {
                        withTimeout(15000L) {
                            geminiService.extractSkillsFromText(req.text)
                        }
                    } catch (e: Exception) {
                        val fallbackSkills = skillExtractionService.extractSkills(req.text)
                        GeminiExtractionResult(
                            profileSummary = null,
                            skills = fallbackSkills,
                            warning = "Đang xử lý ở chế độ ngoại tuyến."
                        )
                    }
                } else {
                    val fallbackSkills = skillExtractionService.extractSkills(req.text)
                    GeminiExtractionResult(
                        profileSummary = null,
                        skills = fallbackSkills,
                        warning = null
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    ExtractSkillsResponse(
                        success = true,
                        data = extractionResult.skills,
                        profileSummary = extractionResult.profileSummary,
                        warning = extractionResult.warning
                    )
                )
            }

            post("/extract-skills-document") {
                val userId = try {
                    call.getUserId()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            status = 401,
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Chưa xác thực hoặc token không hợp lệ"
                        )
                    )
                }

                // 1. Kiểm tra rate limit: 15 requests/phút/user
                if (!documentRateLimiter.tryAcquire(userId)) {
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đã gửi quá nhiều yêu cầu tải tài liệu. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                // 2. Kiểm tra giới hạn số request xử lý đồng thời (Concurrency limiter: tối đa 5)
                if (!documentConcurrencyLimiter.tryAcquire()) {
                    call.response.header("Retry-After", "5")
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiError(
                            status = 503,
                            code = ErrorCodes.SERVER_BUSY,
                            message = "Hệ thống đang xử lý nhiều tài liệu, vui lòng thử lại sau giây lát."
                        )
                    )
                }

                try {
                    val isJson = try {
                        call.request.contentType().match(ContentType.Application.Json)
                    } catch (e: Exception) {
                        false
                    }

                    val extractionResult = withContext(Dispatchers.IO) {
                        withTimeout(25000L) {
                            val coroutineCtx = currentCoroutineContext()
                            if (isJson) {
                                val req = try {
                                    call.receive<ExtractSkillsDocumentRequest>()
                                } catch (e: Exception) {
                                    throw DocumentParseException(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorCodes.VALIDATION_FAILED,
                                        "Dữ liệu JSON không hợp lệ"
                                    )
                                }

                                if (geminiService != null) {
                                    try {
                                        geminiService.extractSkillsFromText(req.text)
                                    } catch (e: Exception) {
                                        GeminiExtractionResult(
                                            profileSummary = null,
                                            skills = emptyList(),
                                            warning = "Không thể phân tích văn bản qua AI: ${e.message ?: "Lỗi xử lý"}"
                                        )
                                    }
                                } else {
                                    GeminiExtractionResult(
                                        profileSummary = null,
                                        skills = emptyList(),
                                        warning = "Dịch vụ AI chưa được kích hoạt."
                                    )
                                }
                            } else {
                                // Multipart form-data (Tài liệu PDF/TXT hoặc hình ảnh chứng chỉ/CV)
                                var fileBytes: ByteArray? = null
                                var fileName = "document.txt"
                                var mimeType = "application/octet-stream"
                                var foundFile = false

                                val multipart = try {
                                    call.receiveMultipart()
                                } catch (e: Exception) {
                                    throw DocumentParseException(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorCodes.VALIDATION_FAILED,
                                        "Yêu cầu tải tệp multipart không hợp lệ"
                                    )
                                }

                                multipart.forEachPart { part ->
                                    if (part is PartData.FileItem && !foundFile) {
                                        foundFile = true
                                        fileName = part.originalFileName ?: "document.txt"
                                        mimeType = part.contentType?.toString() ?: "application/octet-stream"
                                        part.streamProvider().use { stream ->
                                            val buffer = ByteArrayOutputStream()
                                            val temp = ByteArray(8192)
                                            var read: Int
                                            var total = 0
                                            val maxAllowed = 10 * 1024 * 1024 // 10MB
                                            while (stream.read(temp).also { read = it } != -1) {
                                                total += read
                                                if (total > maxAllowed) {
                                                    throw DocumentParseException(
                                                        HttpStatusCode.PayloadTooLarge,
                                                        ErrorCodes.PAYLOAD_TOO_LARGE,
                                                        "Kích thước tệp vượt quá giới hạn 10MB."
                                                    )
                                                }
                                                buffer.write(temp, 0, read)
                                            }
                                            fileBytes = buffer.toByteArray()
                                        }
                                    }
                                    part.dispose()
                                }

                                if (!foundFile || fileBytes == null || fileBytes!!.isEmpty()) {
                                    throw DocumentParseException(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorCodes.EMPTY_FILE,
                                        "Vui lòng đính kèm tệp tài liệu hoặc hình ảnh trong yêu cầu."
                                    )
                                }

                                val bytes = fileBytes!!
                                if (geminiService != null) {
                                    try {
                                        geminiService.extractSkillsFromMultimodal(bytes, mimeType, fileName)
                                    } catch (geminiEx: Exception) {
                                        GeminiExtractionResult(
                                            profileSummary = null,
                                            skills = emptyList(),
                                            warning = "Không thể phân tích tệp qua AI: ${geminiEx.message ?: "Lỗi xử lý"}"
                                        )
                                    }
                                } else {
                                    GeminiExtractionResult(
                                        profileSummary = null,
                                        skills = emptyList(),
                                        warning = "Dịch vụ AI chưa được kích hoạt."
                                    )
                                }
                            }
                        }
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        ExtractSkillsResponse(
                            success = true,
                            data = extractionResult.skills,
                            warning = extractionResult.warning,
                            profileSummary = extractionResult.profileSummary
                        )
                    )
                } catch (e: TimeoutCancellationException) {
                    call.respond(
                        HttpStatusCode.GatewayTimeout,
                        ApiError(
                            status = 504,
                            code = ErrorCodes.QUERY_TIMEOUT,
                            message = "Xử lý tài liệu quá thời gian quy định (timeout 25s). Vui lòng thử lại với tệp nhỏ hơn."
                        )
                    )
                } catch (e: DocumentParseException) {
                    call.respond(
                        e.statusCode,
                        ApiError(
                            status = e.statusCode.value,
                            code = e.errorCode,
                            message = e.message
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(
                            status = 500,
                            code = ErrorCodes.INTERNAL,
                            message = e.message ?: "Đã xảy ra lỗi khi xử lý tài liệu"
                        )
                    )
                } finally {
                    documentConcurrencyLimiter.release()
                }
            }
        }
    }
}
