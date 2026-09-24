package com.skillexchange.app.data.remote.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Remote data source — gọi Ktor API server.
 * Không chứa business logic, chỉ serialize/deserialize.
 */
class AuthRemoteDataSource(private val client: HttpClient) {

    suspend fun register(dto: RegisterRequestDto): ApiWrapper<RegisterResponseDto> =
        client.post("/api/auth/register") { setBody(dto) }.body()

    suspend fun login(dto: LoginRequestDto): ApiWrapper<AuthSessionDto> =
        client.post("/api/auth/login") { setBody(dto) }.body()

    suspend fun verifyOtp(dto: VerifyOtpRequestDto): ApiWrapper<AuthSessionDto> =
        client.post("/api/auth/verify-otp") { setBody(dto) }.body()

    suspend fun refresh(dto: RefreshRequestDto): ApiWrapper<AuthSessionDto> =
        client.post("/api/auth/refresh") { setBody(dto) }.body()
}
