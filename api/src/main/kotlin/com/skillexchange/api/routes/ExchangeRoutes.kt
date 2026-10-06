package com.skillexchange.api.routes

import com.skillexchange.api.models.exchange.CancelExchangeRequest
import com.skillexchange.api.models.exchange.CreateExchangeRequest
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.exchange.ExchangeConflictException
import com.skillexchange.api.services.exchange.ExchangeForbiddenException
import com.skillexchange.api.services.exchange.ExchangeService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.exchangeRoutes(exchangeService: ExchangeService) {

    route("/api/exchange-requests") {
        authenticate("auth-jwt") {

            // POST /api/exchange-requests - Tạo yêu cầu mới
            post {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CreateExchangeRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Dữ liệu yêu cầu không hợp lệ", code = 400)
                    )
                }

                try {
                    val result = exchangeService.createExchangeRequest(callerUserId, req)
                    call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = e.message ?: "Dữ liệu không hợp lệ", code = 422)
                    )
                }
            }

            // GET /api/exchange-requests/incoming - Yêu cầu gửi đến người gọi
            get("/incoming") {
                val callerUserId = call.getUserId()
                val list = exchangeService.getIncomingRequests(callerUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = list))
            }

            // GET /api/exchange-requests/outgoing - Yêu cầu người gọi đã gửi
            get("/outgoing") {
                val callerUserId = call.getUserId()
                val list = exchangeService.getOutgoingRequests(callerUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = list))
            }

            // GET /api/exchange-requests/{id} - Chi tiết yêu cầu
            get("/{id}") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu id yêu cầu", code = 400)
                    )

                try {
                    val result = exchangeService.getExchangeRequest(callerUserId, exchangeId)
                    if (result == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            RouteApiError(message = "Không tìm thấy yêu cầu trao đổi", code = 404)
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                    }
                } catch (e: ExchangeForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        RouteApiError(message = e.message ?: "Không có quyền truy cập", code = 403)
                    )
                }
            }

            // PUT /api/exchange-requests/{id}/accept - Chấp nhận yêu cầu (Receiver)
            put("/{id}/accept") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu id yêu cầu", code = 400)
                    )

                try {
                    val result = exchangeService.acceptRequest(callerUserId, exchangeId)
                    if (result == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            RouteApiError(message = "Không tìm thấy yêu cầu trao đổi", code = 404)
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                    }
                } catch (e: ExchangeForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        RouteApiError(message = e.message ?: "Không có quyền thao tác", code = 403)
                    )
                } catch (e: ExchangeConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        RouteApiError(message = e.message ?: "Xung đột trạng thái", code = 409)
                    )
                }
            }

            // PUT /api/exchange-requests/{id}/reject - Từ chối yêu cầu (Receiver)
            put("/{id}/reject") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu id yêu cầu", code = 400)
                    )

                try {
                    val result = exchangeService.rejectRequest(callerUserId, exchangeId)
                    if (result == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            RouteApiError(message = "Không tìm thấy yêu cầu trao đổi", code = 404)
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                    }
                } catch (e: ExchangeForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        RouteApiError(message = e.message ?: "Không có quyền thao tác", code = 403)
                    )
                } catch (e: ExchangeConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        RouteApiError(message = e.message ?: "Xung đột trạng thái", code = 409)
                    )
                }
            }

            // PUT /api/exchange-requests/{id}/cancel - Hủy yêu cầu (Sender khi PENDING, cả hai khi ACCEPTED)
            put("/{id}/cancel") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu id yêu cầu", code = 400)
                    )

                val body = try {
                    call.receive<CancelExchangeRequest>()
                } catch (e: Exception) {
                    null
                }

                try {
                    val result = exchangeService.cancelRequest(callerUserId, exchangeId, body?.reason)
                    if (result == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            RouteApiError(message = "Không tìm thấy yêu cầu trao đổi", code = 404)
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                    }
                } catch (e: ExchangeForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        RouteApiError(message = e.message ?: "Không có quyền thao tác", code = 403)
                    )
                } catch (e: ExchangeConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        RouteApiError(message = e.message ?: "Xung đột trạng thái", code = 409)
                    )
                }
            }

            // PUT /api/exchange-requests/{id}/complete - Xác nhận hoàn thành
            put("/{id}/complete") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Thiếu id yêu cầu", code = 400)
                    )

                try {
                    val result = exchangeService.completeRequest(callerUserId, exchangeId)
                    if (result == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            RouteApiError(message = "Không tìm thấy yêu cầu trao đổi", code = 404)
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                    }
                } catch (e: ExchangeForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        RouteApiError(message = e.message ?: "Không có quyền thao tác", code = 403)
                    )
                } catch (e: ExchangeConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        RouteApiError(message = e.message ?: "Xung đột trạng thái", code = 409)
                    )
                }
            }
        }
    }
}
