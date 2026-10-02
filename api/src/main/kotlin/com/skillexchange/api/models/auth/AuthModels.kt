package com.skillexchange.api.models.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Inbound Requests (từ Android App) ───────────────────────────────

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val token: String,                  // 6-digit OTP code
    val type: String = "signup"         // "signup" | "email" | "recovery"
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

// ─── Outbound Responses (trả về Android App) ─────────────────────────

@Serializable
data class AuthResponse(
    val success: Boolean = true,
    @SerialName("access_token")  val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id")       val userId: String,
    val email: String,
    @SerialName("expires_in")    val expiresIn: Int = 3600  // seconds
)

@Serializable
data class RegisterResponse(
    val success: Boolean = true,
    val message: String = "OTP đã được gửi đến email của bạn",
    val email: String
)

// ─── Supabase Auth API Payloads (internal) ───────────────────────────

@Serializable
internal data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap()  // user_metadata (fullName)
)

@Serializable
internal data class SupabaseSignInRequest(
    val email: String,
    val password: String
)

@Serializable
internal data class SupabaseVerifyRequest(
    val email: String,
    val token: String,
    val type: String
)

@Serializable
internal data class SupabaseRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

// ─── Supabase Auth API Response (internal) ───────────────────────────

@Serializable
internal data class SupabaseSessionResponse(
    @SerialName("access_token")  val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in")    val expiresIn: Int? = null,
    val user: SupabaseUser? = null,
    // Error fields
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val message: String? = null,
    val code: Int? = null
)

@Serializable
internal data class SupabaseUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null
)
