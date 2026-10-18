package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.ValidationConstants
import com.skillexchange.api.models.auth.LoginRequest
import com.skillexchange.api.models.auth.RefreshRequest
import com.skillexchange.api.models.auth.RegisterRequest
import com.skillexchange.api.models.auth.VerifyOtpRequest
import com.skillexchange.api.services.AuthService
import com.skillexchange.api.services.auth.AuthRateLimiter
import com.skillexchange.api.services.auth.IFirebaseTokenService
import com.skillexchange.api.services.auth.getClientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ApiSuccess<T>(val success: Boolean = true, val data: T)

@Serializable
data class RouteApiError(val success: Boolean = false, val message: String, val code: Int = 400)

@Serializable
data class FirebaseTokenResponse(val token: String, val expiresIn: Int = 3600)

private val logger = LoggerFactory.getLogger("AuthRoutes")

/**
 * Auth routes — authService và firebaseTokenService được inject ở Application level (Routing.kt)
 * và truyền vào đây, tránh bug Route.inject() trong Koin 4.x + Ktor 3.x
 */
fun Route.authRoutes(
    authService: AuthService,
    firebaseTokenService: IFirebaseTokenService,
    authRateLimiter: AuthRateLimiter = AuthRateLimiter()
) {

    route("/api/auth") {

        // POST /api/auth/register
        post("/register") {
            val ip = call.getClientIp()
            if (!authRateLimiter.tryAcquireRegister(ip)) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError(
                        status = 429,
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Bạn đã thực hiện quá nhiều lượt đăng ký. Vui lòng thử lại sau ít phút."
                    )
                )
            }

            val req = try { call.receive<RegisterRequest>() } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Dữ liệu không hợp lệ")
                )
            }

            if (req.email.isBlank() || !req.email.contains("@")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Email không hợp lệ")
                )
            }
            if (req.email.length > ValidationConstants.MAX_EMAIL_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Email không được vượt quá ${ValidationConstants.MAX_EMAIL_LENGTH} ký tự"
                    )
                )
            }
            if (req.password.length < ValidationConstants.MIN_PASSWORD_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = 400,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Mật khẩu phải có ít nhất ${ValidationConstants.MIN_PASSWORD_LENGTH} ký tự"
                    )
                )
            }
            if (req.password.length > ValidationConstants.MAX_PASSWORD_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Mật khẩu không được vượt quá ${ValidationConstants.MAX_PASSWORD_LENGTH} ký tự"
                    )
                )
            }
            if (req.fullName.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Họ tên không được để trống")
                )
            }
            if (req.fullName.length > ValidationConstants.MAX_FULL_NAME_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Họ tên không được vượt quá ${ValidationConstants.MAX_FULL_NAME_LENGTH} ký tự"
                    )
                )
            }

            val cleanEmail = req.email.trim()
            try {
                val result = authService.register(cleanEmail, req.password, req.fullName.trim())
                call.respond(HttpStatusCode.Created, ApiSuccess(data = result))
            } catch (e: Exception) {
                val maskedEmail = if (cleanEmail.length > 4) cleanEmail.take(3) + "***" else "***"
                logger.warn("User register failed for $maskedEmail from IP $ip: ${e.message}")
                val isExisting = e.message?.contains("already", ignoreCase = true) == true ||
                                 e.message?.contains("exists", ignoreCase = true) == true
                val clientMessage = if (isExisting) "Email này đã được đăng ký hoặc không hợp lệ" else "Đăng ký thất bại. Vui lòng kiểm tra lại thông tin."
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = clientMessage)
                )
            }
        }

        // POST /api/auth/login
        post("/login") {
            val req = try { call.receive<LoginRequest>() } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = 400,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Dữ liệu không hợp lệ"
                    )
                )
            }

            val ip = call.getClientIp()
            val cleanEmail = req.email.trim()

            if (!authRateLimiter.tryAcquireLogin(cleanEmail, ip)) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError(
                        status = 429,
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 1 phút."
                    )
                )
            }

            if (cleanEmail.isBlank() || req.password.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(
                        status = 400,
                        code = ErrorCodes.BAD_REQUEST,
                        message = "Email và mật khẩu không được để trống"
                    )
                )
            }

            if (cleanEmail.length > ValidationConstants.MAX_EMAIL_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Email không được vượt quá ${ValidationConstants.MAX_EMAIL_LENGTH} ký tự"
                    )
                )
            }

            if (req.password.length > ValidationConstants.MAX_PASSWORD_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Mật khẩu không được vượt quá ${ValidationConstants.MAX_PASSWORD_LENGTH} ký tự"
                    )
                )
            }

            try {
                val result = authService.login(cleanEmail, req.password)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                val maskedEmail = if (cleanEmail.length > 4) cleanEmail.take(3) + "***" else "***"
                logger.warn("Login failed for $maskedEmail from IP $ip: ${e.message}")
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError(
                        status = 401,
                        code = ErrorCodes.UNAUTHORIZED,
                        message = "Email hoặc mật khẩu không đúng"
                    )
                )
            }
        }

        // POST /api/auth/verify-otp
        post("/verify-otp") {
            val req = try { call.receive<VerifyOtpRequest>() } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Dữ liệu không hợp lệ")
                )
            }

            val ip = call.getClientIp()
            val cleanEmail = req.email.trim()

            if (!authRateLimiter.tryAcquireOtp(cleanEmail, ip)) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError(
                        status = 429,
                        code = ErrorCodes.RATE_LIMITED,
                        message = "Bạn đã nhập mã OTP sai quá nhiều lần. Vui lòng thử lại sau 1 phút."
                    )
                )
            }

            if (cleanEmail.isBlank() || req.token.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Email và mã OTP không được để trống")
                )
            }

            if (cleanEmail.length > ValidationConstants.MAX_EMAIL_LENGTH) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        status = 422,
                        code = ErrorCodes.VALIDATION_FAILED,
                        message = "Email không được vượt quá ${ValidationConstants.MAX_EMAIL_LENGTH} ký tự"
                    )
                )
            }

            if (req.token.length !in 6..8 || !req.token.all { it.isDigit() }) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Mã OTP phải từ 6 đến 8 chữ số")
                )
            }

            try {
                val result = authService.verifyOtp(cleanEmail, req.token, req.type)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                val maskedEmail = if (cleanEmail.length > 4) cleanEmail.take(3) + "***" else "***"
                logger.warn("Verify OTP failed for $maskedEmail from IP $ip: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Mã OTP không hợp lệ hoặc đã hết hạn")
                )
            }
        }

        // POST /api/auth/refresh
        post("/refresh") {
            val req = try { call.receive<RefreshRequest>() } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "Dữ liệu không hợp lệ")
                )
            }
            if (req.refreshToken.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(status = 400, code = ErrorCodes.BAD_REQUEST, message = "refresh_token không được để trống")
                )
            }

            try {
                val result = authService.refreshToken(req.refreshToken)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = result))
            } catch (e: Exception) {
                logger.warn("Token refresh failed: ${e.message}")
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError(status = 401, code = ErrorCodes.UNAUTHORIZED, message = "Phiên đăng nhập đã hết hạn hoặc refresh token không hợp lệ")
                )
            }
        }

        // POST /api/auth/firebase-token (Yêu cầu JWT)
        authenticate("auth-jwt") {
            post("/firebase-token") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.subject
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError(
                            status = 401,
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Chưa xác thực. Vui lòng đăng nhập."
                        )
                    )

                try {
                    val customToken = firebaseTokenService.createCustomToken(userId)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiSuccess(data = FirebaseTokenResponse(token = customToken, expiresIn = 3600))
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(
                            status = 500,
                            code = ErrorCodes.INTERNAL,
                            message = "Không thể khởi tạo phiên trò chuyện lúc này."
                        )
                    )
                }
            }
        }
    }
}
