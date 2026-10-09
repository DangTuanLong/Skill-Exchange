package com.skillexchange.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Requests ──────────────────────────────────────────────────────────
@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class VerifyOtpRequestDto(
    val email: String,
    val token: String,
    val type: String = "signup"
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)

// ─── Responses ──────────────────────────────────────────────────────────
@Serializable
data class ApiWrapper<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null,
    val status: Int? = null,
    @Serializable(with = com.skillexchange.app.core.network.LenientCodeSerializer::class)
    val code: String? = null
)

@Serializable
data class AuthSessionDto(
    @SerialName("access_token")  val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id")       val userId: String,
    val email: String,
    @SerialName("expires_in")    val expiresIn: Int = 3600
)

@Serializable
data class RegisterResponseDto(
    val message: String,
    val email: String
)

@Serializable
data class FirebaseTokenResponseDto(
    val token: String,
    val expiresIn: Int = 3600
)
