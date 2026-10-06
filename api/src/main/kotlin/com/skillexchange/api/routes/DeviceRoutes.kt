package com.skillexchange.api.routes

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
                        RouteApiError(message = "Dữ liệu yêu cầu không hợp lệ", code = 400)
                    )
                }

                if (req.token.isBlank() || req.token.length > 4096) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "FCM token không hợp lệ", code = 400)
                    )
                }

                if (req.deviceInfo != null && req.deviceInfo.length > 200) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thông tin thiết bị không được vượt quá 200 ký tự", code = 400)
                    )
                }

                try {
                    deviceService.registerToken(callerUserId, req.token, req.deviceInfo)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiSuccess(
                            data = DeviceResponse(
                                token = req.token,
                                message = "Đăng ký thiết bị thành công"
                            )
                        )
                    )
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = e.message ?: "Dữ liệu không hợp lệ", code = 422)
                    )
                }
            }

            // DELETE /api/devices - Xóa token FCM (khi logout hoặc hủy thiết bị)
            delete {
                val callerUserId = call.getUserId()
                val token = call.request.queryParameters["token"]
                    ?: try { call.receiveNullable<DeleteDeviceRequest>()?.token } catch (_: Exception) { null }

                if (token.isNullOrBlank()) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu tham số token để xóa", code = 400)
                    )
                }

                deviceService.deleteToken(callerUserId, token)
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccess(
                        data = DeviceResponse(
                            token = token,
                            message = "Xóa thiết bị thành công"
                        )
                    )
                )
            }
        }
    }
}
