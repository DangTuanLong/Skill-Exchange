package com.skillexchange.api.plugins

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

// Typealias for backward compatibility with existing imports in tests or code
typealias ApiError = com.skillexchange.api.models.ApiError

open class ValidationException(
    message: String,
    val fieldErrors: Map<String, String>? = null
) : IllegalArgumentException(message)

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        // 401 Unauthorized
        status(HttpStatusCode.Unauthorized) { call, status ->
            val message = if (call.request.local.uri.contains("/login") || call.request.uri.contains("/login")) {
                "Email hoặc mật khẩu không đúng"
            } else {
                "Chưa xác thực. Vui lòng đăng nhập."
            }
            call.respond(
                status,
                ApiError(
                    status = status.value,
                    code = ErrorCodes.UNAUTHORIZED,
                    message = message
                )
            )
        }

        // 403 Forbidden
        status(HttpStatusCode.Forbidden) { call, status ->
            call.respond(
                status,
                ApiError(
                    status = status.value,
                    code = ErrorCodes.FORBIDDEN,
                    message = "Không có quyền truy cập."
                )
            )
        }

        // 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiError(
                    status = status.value,
                    code = ErrorCodes.NOT_FOUND,
                    message = "Không tìm thấy tài nguyên yêu cầu."
                )
            )
        }

        // 413 Payload Too Large (bảo tồn route unmigrated /avatar)
        status(HttpStatusCode.PayloadTooLarge) { call, status ->
            if (!call.request.uri.contains("/avatar")) {
                call.respond(
                    status,
                    ApiError(
                        status = status.value,
                        code = ErrorCodes.PAYLOAD_TOO_LARGE,
                        message = "Kích thước tập tin vượt quá giới hạn cho phép."
                    )
                )
            }
        }

        // 415 Unsupported Media Type (bảo tồn route unmigrated /avatar)
        status(HttpStatusCode.UnsupportedMediaType) { call, status ->
            if (!call.request.uri.contains("/avatar")) {
                call.respond(
                    status,
                    ApiError(
                        status = status.value,
                        code = ErrorCodes.UNSUPPORTED_MEDIA,
                        message = "Định dạng tập tin không được hỗ trợ."
                    )
                )
            }
        }

        // 429 Too Many Requests (bảo tồn route unmigrated /avatar)
        status(HttpStatusCode.TooManyRequests) { call, status ->
            if (!call.request.uri.contains("/avatar")) {
                call.respond(
                    status,
                    ApiError(
                        status = status.value,
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút."
                    )
                )
            }
        }

        // ValidationException — 422 Unprocessable Entity
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiError(
                    status = HttpStatusCode.UnprocessableEntity.value,
                    code = ErrorCodes.VALIDATION_FAILED,
                    message = cause.message ?: "Dữ liệu nhập không hợp lệ.",
                    fieldErrors = cause.fieldErrors
                )
            )
        }

        // IllegalArgumentException — 400 Bad Request
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(
                    status = HttpStatusCode.BadRequest.value,
                    code = ErrorCodes.BAD_REQUEST,
                    message = cause.message ?: "Dữ liệu không hợp lệ."
                )
            )
        }

        // Generic Exception handler — 500 Internal Server Error
        exception<Throwable> { call, cause ->
            // Chỉ log chi tiết phía server, tuyệt đối không lộ nội dung thô hay stack trace ra client
            logger.error("Unhandled server exception: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    status = HttpStatusCode.InternalServerError.value,
                    code = ErrorCodes.INTERNAL,
                    message = "Lỗi máy chủ. Vui lòng thử lại sau."
                )
            )
        }
    }
}
