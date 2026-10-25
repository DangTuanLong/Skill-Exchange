package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.ValidationConstants
import com.skillexchange.api.models.device.DeleteDeviceRequest
import com.skillexchange.api.models.device.DeviceResponse
import com.skillexchange.api.models.device.RegisterDeviceRequest
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.device.DeviceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun maskDeviceToken(token: String): String {
    return if (token.length > 8) "${token.take(4)}...${token.takeLast(4)}" else "***"
}

fun Route.deviceRoutes(deviceService: DeviceService) {

    route("/api/devices") {
        authenticate("auth-jwt") {

            // POST /api/devices - Lưu / cập nhật token FCM
            post {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<RegisterDeviceRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = 400,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ"
                        )
                    )
                }

                if (req.token.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = 400,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "FCM token không được để trống"
                        )
                    )
                }

                if (req.token.length > ValidationConstants.MAX_FCM_TOKEN_LENGTH) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "FCM token vượt quá độ dài cho phép (${ValidationConstants.MAX_FCM_TOKEN_LENGTH} ký tự)"
                        )
                    )
                }

                if (req.deviceInfo != null && req.deviceInfo.length > ValidationConstants.MAX_DEVICE_INFO_LENGTH) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "Thông tin thiết bị không được vượt quá ${ValidationConstants.MAX_DEVICE_INFO_LENGTH} ký tự"
                        )
                    )
                }

                try {
                    deviceService.registerToken(callerUserId, req.token.trim(), req.deviceInfo?.trim())
                    call.respond(
                        HttpStatusCode.OK,
                        ApiSuccess(
                            data = DeviceResponse(
                                token = maskDeviceToken(req.token.trim()),
                                message = "Đăng ký thiết bị thành công"
                            )
                        )
                    )
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ"
                        )
                    )
                }
            }

            // DELETE /api/devices - Xóa token FCM (khi logout hoặc hủy thiết bị, chỉ nhận token qua JSON body)
            delete {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receiveNullable<DeleteDeviceRequest>()
                } catch (_: Exception) {
                    null
                }
                val token = req?.token?.trim()

                if (token.isNullOrBlank()) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = 400,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Thiếu token trong request body để xóa thiết bị"
                        )
                    )
                }

                if (token.length > ValidationConstants.MAX_FCM_TOKEN_LENGTH) {
                    return@delete call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = 422,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = "FCM token vượt quá độ dài cho phép"
                        )
                    )
                }

                deviceService.deleteToken(callerUserId, token)
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccess(
                        data = DeviceResponse(
                            token = maskDeviceToken(token),
                            message = "Xóa thiết bị thành công"
                        )
                    )
                )
            }
        }
    }
}
