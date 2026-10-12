package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.friendship.CreateFriendshipRequest
import com.skillexchange.api.plugins.ValidationException
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.friendship.FriendshipConflictException
import com.skillexchange.api.services.friendship.FriendshipForbiddenException
import com.skillexchange.api.services.friendship.FriendshipNotFoundException
import com.skillexchange.api.services.friendship.FriendshipService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.friendshipRoutes(friendshipService: FriendshipService) {

    route("/api/friendships") {

        authenticate("auth-jwt") {

            // POST /api/friendships - Gửi lời mời kết bạn
            post {
                val callerUserId = call.getUserId()
                val req = try {
                    call.receive<CreateFriendshipRequest>()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu yêu cầu không hợp lệ."
                        )
                    )
                }

                try {
                    val result = friendshipService.sendRequest(callerUserId, req.friendId)
                    call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ."
                        )
                    )
                } catch (e: FriendshipNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = e.message ?: "Không tìm thấy người dùng."
                        )
                    )
                }
            }

            // GET /api/friendships/me - Danh sách bạn bè đã chấp nhận
            get("/me") {
                val callerUserId = call.getUserId()
                val friends = friendshipService.getFriends(callerUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = friends))
            }

            // GET /api/friendships/pending - Danh sách lời mời đang chờ gửi tới tôi
            get("/pending") {
                val callerUserId = call.getUserId()
                val pending = friendshipService.getPendingRequests(callerUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = pending))
            }

            // GET /api/friendships/status/{userId} - Trạng thái bạn bè với 1 user cụ thể
            get("/status/{userId}") {
                val callerUserId = call.getUserId()
                val targetUserId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu ID người dùng."
                    )
                )

                val status = friendshipService.getFriendshipStatus(callerUserId, targetUserId)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = status))
            }

            // PUT /api/friendships/{id}/accept - Chấp nhận lời mời kết bạn
            put("/{id}/accept") {
                val callerUserId = call.getUserId()
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu ID lời mời kết bạn."
                    )
                )

                try {
                    val result = friendshipService.acceptRequest(callerUserId, id)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ."
                        )
                    )
                } catch (e: FriendshipNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = e.message ?: "Không tìm thấy lời mời kết bạn."
                        )
                    )
                } catch (e: FriendshipForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.FORBIDDEN,
                            message = e.message ?: "Bạn không có quyền chấp nhận lời mời này."
                        )
                    )
                } catch (e: FriendshipConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = ErrorCodes.CONFLICT,
                            message = e.message ?: "Trạng thái không hợp lệ."
                        )
                    )
                }
            }

            // PUT /api/friendships/{id}/reject - Từ chối lời mời kết bạn
            put("/{id}/reject") {
                val callerUserId = call.getUserId()
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu ID lời mời kết bạn."
                    )
                )

                try {
                    val result = friendshipService.rejectRequest(callerUserId, id)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ."
                        )
                    )
                } catch (e: FriendshipNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = e.message ?: "Không tìm thấy lời mời kết bạn."
                        )
                    )
                } catch (e: FriendshipForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.FORBIDDEN,
                            message = e.message ?: "Bạn không có quyền từ chối lời mời này."
                        )
                    )
                } catch (e: FriendshipConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            status = HttpStatusCode.Conflict.value,
                            code = ErrorCodes.CONFLICT,
                            message = e.message ?: "Trạng thái không hợp lệ."
                        )
                    )
                }
            }

            // DELETE /api/friendships/{id} - Hủy bạn bè hoặc hủy lời mời
            delete("/{id}") {
                val callerUserId = call.getUserId()
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = HttpStatusCode.BadRequest.value,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Thiếu ID lời mời kết bạn."
                    )
                )

                try {
                    friendshipService.removeFriendship(callerUserId, id)
                    call.respond(HttpStatusCode.OK, ApiSuccess(data = mapOf("message" to "Đã xóa quan hệ bạn bè thành công.")))
                } catch (e: ValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(
                            status = HttpStatusCode.UnprocessableEntity.value,
                            code = ErrorCodes.VALIDATION_FAILED,
                            message = e.message ?: "Dữ liệu không hợp lệ."
                        )
                    )
                } catch (e: FriendshipForbiddenException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.FORBIDDEN,
                            message = e.message ?: "Bạn không có quyền thực hiện thao tác này."
                        )
                    )
                }
            }
        }
    }
}
