package com.skillexchange.api.services

import com.skillexchange.api.models.auth.AuthResponse
import com.skillexchange.api.models.auth.RegisterResponse
import com.skillexchange.api.models.auth.SupabaseRefreshRequest
import com.skillexchange.api.models.auth.SupabaseSessionResponse
import com.skillexchange.api.models.auth.SupabaseSignInRequest
import com.skillexchange.api.models.auth.SupabaseSignUpRequest
import com.skillexchange.api.models.auth.SupabaseVerifyRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

import com.skillexchange.api.config.EnvLoader

/**
 * Service giao tiếp với Supabase Auth REST API.
 * Supabase URL và API key đọc từ environment variables hoặc .env file.
 */
class AuthService {

    private val supabaseUrl   = EnvLoader.get("SUPABASE_URL", "https://nleafbmggblqggttnfoa.supabase.co")
    private val supabaseAnon  = EnvLoader.get("SUPABASE_ANON_KEY", "")
    private val supabaseService = EnvLoader.get("SUPABASE_SERVICE_KEY", "")

    private val authBaseUrl = "$supabaseUrl/auth/v1"

    // HttpClient riêng cho Supabase calls (dùng CIO engine — lightweight)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnon)
        }
    }

    /**
     * Đăng ký tài khoản mới.
     * Supabase sẽ tự gửi OTP email xác thực.
     */
    suspend fun register(email: String, password: String, fullName: String): RegisterResponse {
        val response: SupabaseSessionResponse = client.post("$authBaseUrl/signup") {
            setBody(SupabaseSignUpRequest(
                email = email,
                password = password,
                data = mapOf("full_name" to fullName)
            ))
        }.body()

        println("[AuthService] Supabase signup response for $email: error=${response.error}, errorDesc=${response.errorDescription}, user=${response.user?.id}")

        // Supabase trả lỗi trong body (không dùng HTTP error codes nhất quán)
        if (response.error != null) {
            val msg = response.errorDescription ?: response.error
            throw IllegalArgumentException(msg)
        }

        return RegisterResponse(email = email)
    }

    /**
     * Đăng nhập bằng email + password.
     * Trả về access_token + refresh_token nếu email đã xác thực.
     */
    suspend fun login(email: String, password: String): AuthResponse {
        val response: SupabaseSessionResponse = client.post(
            "$authBaseUrl/token?grant_type=password"
        ) {
            setBody(SupabaseSignInRequest(email = email, password = password))
        }.body()

        if (response.error != null || response.accessToken == null) {
            val msg = response.errorDescription
                ?: response.message
                ?: response.error
                ?: "Sai email hoặc mật khẩu"
            throw IllegalArgumentException(msg)
        }

        return AuthResponse(
            accessToken  = response.accessToken,
            refreshToken = response.refreshToken ?: "",
            userId       = response.user?.id ?: "",
            email        = response.user?.email ?: email,
            expiresIn    = response.expiresIn ?: 3600
        )
    }

    /**
     * Xác thực OTP từ email.
     * type = "signup" (đăng ký) | "email" (magic link)
     */
    suspend fun verifyOtp(email: String, token: String, type: String = "signup"): AuthResponse {
        val response: SupabaseSessionResponse = client.post("$authBaseUrl/verify") {
            setBody(SupabaseVerifyRequest(email = email, token = token, type = type))
        }.body()

        if (response.error != null || response.accessToken == null) {
            val msg = response.errorDescription
                ?: response.message
                ?: "OTP không hợp lệ hoặc đã hết hạn"
            throw IllegalArgumentException(msg)
        }

        return AuthResponse(
            accessToken  = response.accessToken,
            refreshToken = response.refreshToken ?: "",
            userId       = response.user?.id ?: "",
            email        = response.user?.email ?: email,
            expiresIn    = response.expiresIn ?: 3600
        )
    }

    /**
     * Làm mới access_token bằng refresh_token.
     */
    suspend fun refreshToken(refreshToken: String): AuthResponse {
        val response: SupabaseSessionResponse = client.post(
            "$authBaseUrl/token?grant_type=refresh_token"
        ) {
            setBody(SupabaseRefreshRequest(refreshToken = refreshToken))
        }.body()

        if (response.error != null || response.accessToken == null) {
            val msg = response.errorDescription
                ?: response.message
                ?: "Refresh token không hợp lệ hoặc đã hết hạn"
            throw IllegalArgumentException(msg)
        }

        return AuthResponse(
            accessToken  = response.accessToken,
            refreshToken = response.refreshToken ?: refreshToken,
            userId       = response.user?.id ?: "",
            email        = response.user?.email ?: "",
            expiresIn    = response.expiresIn ?: 3600
        )
    }
}
