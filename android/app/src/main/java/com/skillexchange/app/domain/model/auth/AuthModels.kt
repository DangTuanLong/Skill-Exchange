package com.skillexchange.app.domain.model.auth

/**
 * Domain model — Auth Session sau khi đăng nhập / verify OTP thành công.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresIn: Int = 3600
)

/**
 * Kết quả xác thực sau khi register.
 */
data class RegisterResult(
    val email: String,
    val message: String
)
