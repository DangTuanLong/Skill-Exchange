package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsDocumentRequest
import com.skillexchange.api.models.nlp.ExtractSkillsRequest
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.nlp.DocumentParseException
import com.skillexchange.api.services.nlp.DocumentParser
import com.skillexchange.api.services.nlp.DocumentRateLimiter
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
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.io.ByteArrayInputStream
import java.util.concurrent.Semaphore

fun Route.nlpRoutes(
    skillExtractionService: SkillExtractionService,
    rateLimiter: NlpRateLimiter = NlpRateLimiter(),
    documentRateLimiter: DocumentRateLimiter = DocumentRateLimiter(),
    documentConcurrencyLimiter: Semaphore = Semaphore(3)
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

                val extracted = skillExtractionService.extractSkills(req.text)
                call.respond(HttpStatusCode.OK, ExtractSkillsResponse(success = true, data = extracted))
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

                // 1. Kiểm tra rate limit: 10 requests/phút/user
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

                // 2. Kiểm tra giới hạn số request xử lý đồng thời (Concurrency limiter: tối đa 3)
                if (!documentConcurrencyLimiter.tryAcquire()) {
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

                    val textToAnalyze: String = if (isJson) {
                        val req = try {
                            call.receive<ExtractSkillsDocumentRequest>()
                        } catch (e: Exception) {
                            return@post call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ApiError(
                                    status = 422,
                                    code = ErrorCodes.VALIDATION_FAILED,
                                    message = "Dữ liệu JSON không hợp lệ"
                                )
                            )
                        }
                        DocumentParser.parseTextDocument(
                            ByteArrayInputStream(req.text.toByteArray(Charsets.UTF_8)),
                            req.fileName
                        )
                    } else {
                        // Multipart form-data
                        var extracted: String? = null
                        var foundFile = false
                        val multipart = try {
                            call.receiveMultipart()
                        } catch (e: Exception) {
                            return@post call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ApiError(
                                    status = 422,
                                    code = ErrorCodes.VALIDATION_FAILED,
                                    message = "Yêu cầu tải tệp multipart không hợp lệ"
                                )
                            )
                        }

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem && !foundFile) {
                                foundFile = true
                                val fileName = part.originalFileName ?: "document.txt"
                                part.streamProvider().use { stream ->
                                    extracted = DocumentParser.parseTextDocument(stream, fileName)
                                }
                            }
                            part.dispose()
                        }

                        if (!foundFile || extracted == null) {
                            return@post call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ApiError(
                                    status = 422,
                                    code = ErrorCodes.EMPTY_FILE,
                                    message = "Vui lòng đính kèm tệp tài liệu (.txt hoặc .md) trong yêu cầu."
                                )
                            )
                        }

                        extracted!!
                    }

                    // 3. Phân tích trích xuất kỹ năng bằng dịch vụ in-process (DEC-020)
                    val skills = skillExtractionService.extractSkills(textToAnalyze)
                    call.respond(HttpStatusCode.OK, ExtractSkillsResponse(success = true, data = skills))
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
                            message = "Đã xảy ra lỗi khi xử lý tài liệu"
                        )
                    )
                } finally {
                    documentConcurrencyLimiter.release()
                }
            }
        }
    }
}
