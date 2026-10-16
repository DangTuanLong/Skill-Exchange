package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsRequest
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.nlp.NlpRateLimiter
import com.skillexchange.api.services.nlp.SkillExtractionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.nlpRoutes(
    skillExtractionService: SkillExtractionService,
    rateLimiter: NlpRateLimiter = NlpRateLimiter()
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
        }
    }
}
