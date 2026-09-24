package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.auth.AuthSession
import com.skillexchange.app.domain.model.auth.RegisterResult

/**
 * Auth Repository interface — domain layer không biết gì về Ktor/Retrofit/Room.
 * Implementation nằm ở data layer.
 */
interface IAuthRepository {
    suspend fun register(email: String, password: String, fullName: String): Result<RegisterResult>
    suspend fun login(email: String, password: String): Result<AuthSession>
    suspend fun verifyOtp(email: String, token: String, type: String = "signup"): Result<AuthSession>
    suspend fun refreshToken(refreshToken: String): Result<AuthSession>
}
