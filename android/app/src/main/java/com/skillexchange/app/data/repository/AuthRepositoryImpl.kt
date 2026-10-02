package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import com.skillexchange.app.data.remote.auth.LoginRequestDto
import com.skillexchange.app.data.remote.auth.RefreshRequestDto
import com.skillexchange.app.data.remote.auth.RegisterRequestDto
import com.skillexchange.app.data.remote.auth.VerifyOtpRequestDto
import com.skillexchange.app.domain.model.auth.AuthSession
import com.skillexchange.app.domain.model.auth.RegisterResult
import com.skillexchange.app.domain.repository.IAuthRepository

import com.skillexchange.app.core.security.TokenManager

class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val tokenManager: TokenManager
) : IAuthRepository {

    override suspend fun register(
        email: String, password: String, fullName: String
    ): Result<RegisterResult> = runCatching {
        val resp = remote.register(RegisterRequestDto(email, password, fullName))
        val data = resp.data ?: throw Exception(resp.message ?: "Đăng ký thất bại")
        RegisterResult(email = data.email, message = data.message)
    }

    override suspend fun login(email: String, password: String): Result<AuthSession> =
        runCatching {
            val resp = remote.login(LoginRequestDto(email, password))
            val data = resp.data ?: throw Exception(resp.message ?: "Đăng nhập thất bại")
            tokenManager.saveSession(data.accessToken, data.refreshToken, data.userId)
            data.toDomain()
        }

    override suspend fun verifyOtp(
        email: String, token: String, type: String
    ): Result<AuthSession> = runCatching {
        val resp = remote.verifyOtp(VerifyOtpRequestDto(email, token, type))
        val data = resp.data ?: throw Exception(resp.message ?: "OTP không hợp lệ")
        tokenManager.saveSession(data.accessToken, data.refreshToken, data.userId)
        data.toDomain()
    }

    override suspend fun refreshToken(refreshToken: String): Result<AuthSession> =
        runCatching {
            val resp = remote.refresh(RefreshRequestDto(refreshToken))
            val data = resp.data ?: throw Exception(resp.message ?: "Refresh thất bại")
            tokenManager.saveSession(data.accessToken, data.refreshToken, data.userId)
            data.toDomain()
        }

    private fun com.skillexchange.app.data.remote.auth.AuthSessionDto.toDomain() = AuthSession(
        accessToken  = accessToken,
        refreshToken = refreshToken,
        userId       = userId,
        email        = email,
        expiresIn    = expiresIn
    )
}
