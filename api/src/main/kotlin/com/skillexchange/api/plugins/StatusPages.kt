package com.skillexchange.api.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val success: Boolean = false,
    val message: String,
    val code: Int
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        // 401 Unauthorized
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                ApiError(message = "Chưa xác thực. Vui lòng đăng nhập.", code = 401)
            )
        }

        // 403 Forbidden
        status(HttpStatusCode.Forbidden) { call, status ->
            call.respond(
                status,
                ApiError(message = "Không có quyền truy cập.", code = 403)
            )
        }

        // 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiError(message = "Không tìm thấy tài nguyên yêu cầu.", code = 404)
            )
        }

        // 422 Unprocessable Entity (Validation Error)
        status(HttpStatusCode.UnprocessableEntity) { call, status ->
            call.respond(
                status,
                ApiError(message = "Dữ liệu không hợp lệ.", code = 422)
            )
        }

        // Generic Exception handler
        exception<Throwable> { call, cause ->
            val message = if (System.getenv("KTOR_ENV") == "development") {
                cause.message ?: "Lỗi server không xác định"
            } else {
                "Lỗi server. Vui lòng thử lại sau."
            }
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(message = message, code = 500)
            )
        }

        // IllegalArgumentException — validation errors
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(message = cause.message ?: "Dữ liệu không hợp lệ", code = 400)
            )
        }
    }
}
