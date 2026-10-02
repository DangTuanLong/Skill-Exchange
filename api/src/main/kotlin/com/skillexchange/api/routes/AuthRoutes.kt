package com.skillexchange.api.routes

import com.skillexchange.api.models.auth.LoginRequest
import com.skillexchange.api.models.auth.RefreshRequest
import com.skillexchange.api.models.auth.RegisterRequest
import com.skillexchange.api.models.auth.VerifyOtpRequest
import com.skillexchange.api.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ApiSuccess<T>(val success: Boolean = true, val data: T)

@Serializable
data class RouteApiError(val success: Boolean = false, val message: String, val code: Int = 400)

/**
 * Auth routes — authService được inject ở Application level (Routing.kt)
 * và truyền vào đây, tránh bug Route.inject() trong Koin 4.x + Ktor 3.x
 */
fun Route.authRoutes(authService: AuthService) {

    route("/api/auth") {

        // POST /api/auth/register
        post("/register") {
            val req = try { call.receive<RegisterRequest>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Dữ liệu không hợp lệ", code = 400))
            }
            if (req.email.isBlank() || !req.email.contains("@"))
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Email không hợp lệ", code = 400))
            if (req.password.length < 6)
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Mật khẩu phải có ít nhất 6 ký tự", code = 400))
            if (req.fullName.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Họ tên không được để trống", code = 400))

            try {
                val result = authService.register(req.email.trim(), req.password, req.fullName.trim())
                call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = e.message ?: "Đăng ký thất bại", code = 400))
            }
        }

        // POST /api/auth/login
        post("/login") {
            val req = try { call.receive<LoginRequest>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Dữ liệu không hợp lệ", code = 400))
            }
            if (req.email.isBlank() || req.password.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Email và mật khẩu không được để trống", code = 400))

            try {
                val result = authService.login(req.email.trim(), req.password)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized,
                    RouteApiError(message = e.message ?: "Sai email hoặc mật khẩu", code = 401))
            }
        }

        // POST /api/auth/verify-otp
        post("/verify-otp") {
            val req = try { call.receive<VerifyOtpRequest>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Dữ liệu không hợp lệ", code = 400))
            }
            if (req.email.isBlank() || req.token.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Email và mã OTP không được để trống", code = 400))
            if (req.token.length !in 6..8 || !req.token.all { it.isDigit() })
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Mã OTP phải từ 6 đến 8 chữ số", code = 400))

            try {
                val result = authService.verifyOtp(req.email.trim(), req.token, req.type)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = e.message ?: "OTP không hợp lệ hoặc đã hết hạn", code = 400))
            }
        }

        // POST /api/auth/refresh
        post("/refresh") {
            val req = try { call.receive<RefreshRequest>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "Dữ liệu không hợp lệ", code = 400))
            }
            if (req.refreshToken.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest,
                    RouteApiError(message = "refresh_token không được để trống", code = 400))

            try {
                val result = authService.refreshToken(req.refreshToken)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized,
                    RouteApiError(message = e.message ?: "Refresh token không hợp lệ", code = 401))
            }
        }
    }
}
