package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.call.*
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.call.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.callRoutes(callService: CallService) {

    route("/api/calls") {
        authenticate("auth-jwt") {

            // POST /api/calls/token - Xin cấp token tham gia/khởi tạo phòng gọi
            post("/token") {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CallTokenRequest>()
                } catch (_: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                try {
                    val result = callService.initiateCall(callerUserId, req)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ"
                        )
                    )
                } catch (e: CallForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền truy cập"
                        )
                    )
                } catch (e: CallConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = e.errorCode,
                            message = e.message ?: "Xung đột trạng thái"
                        )
                    )
                } catch (e: CallBusyException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = e.errorCode,
                            message = e.message ?: "Người dùng đang bận"
                        )
                    )
                } catch (e: CallNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy tài nguyên"
                        )
                    )
                } catch (e: CallRateLimitException) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = e.errorCode,
                            message = e.message ?: "Quá giới hạn tần suất gọi"
                        )
                    )
                } catch (e: LiveKitConfigException) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiError(
                            status = HttpStatusCode.ServiceUnavailable.value,
                            code = e.errorCode,
                            message = e.message ?: "Dịch vụ gọi trực tiếp chưa sẵn sàng"
                        )
                    )
                }
            }

            // POST /api/calls/token/join - Receiver lấy token tham gia cuộc gọi đã tạo
            post("/token/join") {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CallActionRequest>()
                } catch (_: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                try {
                    val result = callService.joinCall(callerUserId, req.sessionId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: CallForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền truy cập"
                        )
                    )
                } catch (e: CallConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = e.errorCode,
                            message = e.message ?: "Cuộc gọi đã kết thúc"
                        )
                    )
                } catch (e: CallNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy cuộc gọi"
                        )
                    )
                } catch (e: LiveKitConfigException) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiError(
                            status = HttpStatusCode.ServiceUnavailable.value,
                            code = e.errorCode,
                            message = e.message ?: "Dịch vụ gọi trực tiếp chưa sẵn sàng"
                        )
                    )
                } catch (e: CallRateLimitException) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = e.errorCode,
                            message = e.message ?: "Quá giới hạn tần suất thao tác"
                        )
                    )
                }
            }

            // POST /api/calls/connected - Xác nhận đã kết nối thành công (Receiver)
            post("/connected") {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CallActionRequest>()
                } catch (_: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                try {
                    val result = callService.acceptConnected(callerUserId, req.sessionId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: CallForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền thao tác"
                        )
                    )
                } catch (e: CallConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = e.errorCode,
                            message = e.message ?: "Xung đột trạng thái"
                        )
                    )
                } catch (e: CallNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy cuộc gọi"
                        )
                    )
                } catch (e: CallRateLimitException) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = e.errorCode,
                            message = e.message ?: "Quá giới hạn tần suất thao tác"
                        )
                    )
                }
            }

            // POST /api/calls/reject - Từ chối cuộc gọi (Receiver)
            post("/reject") {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CallActionRequest>()
                } catch (_: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                try {
                    val result = callService.rejectCall(callerUserId, req.sessionId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: CallForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền thao tác"
                        )
                    )
                } catch (e: CallNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy cuộc gọi"
                        )
                    )
                } catch (e: CallRateLimitException) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = e.errorCode,
                            message = e.message ?: "Quá giới hạn tần suất thao tác"
                        )
                    )
                }
            }

            // POST /api/calls/end - Kết thúc cuộc gọi (Caller hoặc Receiver)
            post("/end") {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CallEndRequest>()
                } catch (_: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                try {
                    val result = callService.endCall(callerUserId, req.sessionId)
                    val resp = CallEndResponse(
                        sessionId = result.id,
                        status = result.status,
                        durationSeconds = result.durationSeconds
                    )
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = resp))
                } catch (e: CallForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = e.errorCode,
                            message = e.message ?: "Không có quyền thao tác"
                        )
                    )
                } catch (e: CallNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = e.errorCode,
                            message = e.message ?: "Không tìm thấy cuộc gọi"
                        )
                    )
                } catch (e: CallRateLimitException) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = e.errorCode,
                            message = e.message ?: "Quá giới hạn tần suất thao tác"
                        )
                    )
                }
            }
        }
    }
}
