package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.rating.CreateRatingRequest
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.rating.RatingConflictException
import com.skillexchange.api.services.rating.RatingForbiddenException
import com.skillexchange.api.services.rating.RatingNotFoundException
import com.skillexchange.api.services.rating.RatingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.ratingRoutes(ratingService: RatingService) {

    route("/api/ratings") {

        authenticate("auth-jwt") {
            // POST /api/ratings - Tạo đánh giá mới (chỉ thành viên exchange, exchange phải COMPLETED)
            post {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CreateRatingRequest>()
                } catch (e: Exception) {
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
                    val result = ratingService.createRating(callerUserId, req)
                    call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ",
                            fieldErrors = e.fieldErrors
                        )
                    )
                } catch (e: RatingNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = e.message ?: "Không tìm thấy yêu cầu trao đổi"
                        )
                    )
                } catch (e: RatingForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.NOT_PARTICIPANT,
                            message = e.message ?: "Bạn không phải thành viên của phiên trao đổi này"
                        )
                    )
                } catch (e: RatingConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = ErrorCodes.ALREADY_RATED,
                            message = e.message ?: "Bạn đã đánh giá phiên trao đổi này rồi"
                        )
                    )
                }
            }

            // GET /api/ratings/exchange/{id}/mine - Kiểm tra người gọi đã đánh giá exchange này chưa
            get("/exchange/{id}/mine") {
                val callerUserId = call.getUserId()
                val exchangeId = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Thiếu id yêu cầu trao đổi"
                        )
                    )

                try {
                    val result = ratingService.getMyExchangeRating(callerUserId, exchangeId)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: RatingNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = e.message ?: "Không tìm thấy yêu cầu trao đổi"
                        )
                    )
                } catch (e: RatingForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.NOT_PARTICIPANT,
                            message = e.message ?: "Bạn không phải thành viên của phiên trao đổi này"
                        )
                    )
                }
            }
        }

        // GET /api/ratings/user/{id} - Danh sách đánh giá của một người dùng
        get("/user/{id}") {
            val userId = call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu user id"
                    )
                )

            val list = ratingService.getUserRatings(userId)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = list))
        }
    }

    // GET /api/reputation/{id} - Lấy điểm danh tiếng và huy hiệu tính toán khi đọc
    route("/api/reputation") {
        get("/{id}") {
            val userId = call.parameters["id"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu user id"
                    )
                )

            val reputation = ratingService.getUserReputation(userId)
            call.respond(HttpStatusCode.OK, ApiSuccess(data = reputation))
        }
    }
}
