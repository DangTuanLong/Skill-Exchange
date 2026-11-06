package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.CreateRescheduleRequest
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.exchange.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

const val RESCHEDULE_PROPOSE_RATE_LIMIT_PER_MINUTE = 5
const val RESCHEDULE_ACTION_RATE_LIMIT_PER_MINUTE = 30

/**
 * Rate limiter in-memory theo từng server instance (DEC-025, TASK-072):
 * - Đề xuất đổi lịch mới: 5 req/phút/user (RESCHEDULE_PROPOSE_RATE_LIMIT_PER_MINUTE).
 * - Các thao tác xem/duyệt/từ chối/hủy (current, accept, reject, cancel): 30 req/phút/user (RESCHEDULE_ACTION_RATE_LIMIT_PER_MINUTE).
 * Giới hạn độc lập trên từng instance (in-memory per-instance).
 */
val defaultRescheduleRateLimiter = RescheduleRateLimiter(maxPerMinute = RESCHEDULE_PROPOSE_RATE_LIMIT_PER_MINUTE)
val defaultRescheduleActionRateLimiter = RescheduleRateLimiter(maxPerMinute = RESCHEDULE_ACTION_RATE_LIMIT_PER_MINUTE)

fun Route.rescheduleRoutes(
    rescheduleService: RescheduleService,
    rateLimiter: RescheduleRateLimiter = defaultRescheduleRateLimiter,
    actionRateLimiter: RescheduleRateLimiter = defaultRescheduleActionRateLimiter
) {
    authenticate("auth-jwt") {
        route("/api/exchange-requests/{id}/reschedule") {

            // POST /api/exchange-requests/{id}/reschedule - Gửi đề xuất đổi lịch
            post {
                val callerUserId = call.getUserId()
                if (!rateLimiter.tryAcquire(callerUserId)) {
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu đổi lịch quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val exchangeId = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Thiếu id yêu cầu trao đổi")
                    )

                val req = try {
                    call.receive<CreateRescheduleRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Dữ liệu yêu cầu không hợp lệ")
                    )
                }

                try {
                    val result = rescheduleService.createProposal(callerUserId, exchangeId, req)
                    call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
                } catch (e: RescheduleValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = e.errorCode,
                            message = e.message ?: "Dữ liệu đề xuất không hợp lệ",
                            fieldErrors = e.fieldErrors
                        )
                    )
                } catch (e: RescheduleForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = 403,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền thực hiện thao tác này"
                        )
                    )
                } catch (e: RescheduleConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = 409,
                            code = e.errorCode,
                            message = e.message ?: "Xung đột trạng thái đề xuất đổi lịch"
                        )
                    )
                } catch (e: RescheduleNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = 404,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy yêu cầu trao đổi"
                        )
                    )
                }
            }

            // GET /api/exchange-requests/{id}/reschedule/current - Lấy đề xuất hiện tại
            get("/current") {
                val callerUserId = call.getUserId()
                if (!actionRateLimiter.tryAcquire(callerUserId)) {
                    return@get call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val exchangeId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Thiếu id yêu cầu trao đổi")
                    )

                try {
                    val result = rescheduleService.getCurrentProposal(callerUserId, exchangeId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: RescheduleForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = 403,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền truy cập đề xuất đổi lịch này"
                        )
                    )
                } catch (e: RescheduleNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = 404,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy yêu cầu trao đổi"
                        )
                    )
                }
            }
        }

        route("/api/reschedule-proposals/{proposalId}") {

            // PUT /api/reschedule-proposals/{proposalId}/accept - Chấp nhận đề xuất
            put("/accept") {
                val callerUserId = call.getUserId()
                if (!actionRateLimiter.tryAcquire(callerUserId)) {
                    return@put call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val proposalId = call.parameters["proposalId"] ?: call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Thiếu id đề xuất đổi lịch")
                    )

                try {
                    val result = rescheduleService.acceptProposal(callerUserId, proposalId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: RescheduleForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = 403,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền chấp nhận đề xuất này"
                        )
                    )
                } catch (e: RescheduleConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = 409,
                            code = e.errorCode,
                            message = e.message ?: "Đề xuất đổi lịch không ở trạng thái hợp lệ"
                        )
                    )
                } catch (e: RescheduleNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = 404,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy đề xuất đổi lịch"
                        )
                    )
                }
            }

            // PUT /api/reschedule-proposals/{proposalId}/reject - Từ chối đề xuất
            put("/reject") {
                val callerUserId = call.getUserId()
                if (!actionRateLimiter.tryAcquire(callerUserId)) {
                    return@put call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val proposalId = call.parameters["proposalId"] ?: call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Thiếu id đề xuất đổi lịch")
                    )

                try {
                    val result = rescheduleService.rejectProposal(callerUserId, proposalId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: RescheduleForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = 403,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền từ chối đề xuất này"
                        )
                    )
                } catch (e: RescheduleConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = 409,
                            code = e.errorCode,
                            message = e.message ?: "Đề xuất đổi lịch không ở trạng thái hợp lệ"
                        )
                    )
                } catch (e: RescheduleNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = 404,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy đề xuất đổi lịch"
                        )
                    )
                }
            }

            // PUT /api/reschedule-proposals/{proposalId}/cancel - Hủy đề xuất
            put("/cancel") {
                val callerUserId = call.getUserId()
                if (!actionRateLimiter.tryAcquire(callerUserId)) {
                    return@put call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = 429,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau 1 phút."
                        )
                    )
                }

                val proposalId = call.parameters["proposalId"] ?: call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Thiếu id đề xuất đổi lịch")
                    )

                try {
                    val result = rescheduleService.cancelProposal(callerUserId, proposalId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: RescheduleForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = 403,
                            code = e.errorCode,
                            message = e.message ?: "Chỉ người tạo mới có quyền hủy đề xuất này"
                        )
                    )
                } catch (e: RescheduleConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = 409,
                            code = e.errorCode,
                            message = e.message ?: "Đề xuất đổi lịch không ở trạng thái hợp lệ"
                        )
                    )
                } catch (e: RescheduleNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = 404,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy đề xuất đổi lịch"
                        )
                    )
                }
            }
        }
    }
}

