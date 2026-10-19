package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import io.ktor.client.plugins.defaultRequest

class AuthRepositoryTest {

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
            defaultRequest {
                url("http://localhost")
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    @Test
    fun register_Success_ReturnsRegisterResult() = runTest {
        val json = """{"success":true,"data":{"email":"test@example.com","message":"Đăng ký thành công, vui lòng kiểm tra email"}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.register("test@example.com", "Password@123", "Nguyễn Văn A")

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("test@example.com", data?.email)
        assertEquals("Đăng ký thành công, vui lòng kiểm tra email", data?.message)
    }

    @Test
    fun register_Failure_ReturnsException() = runTest {
        val json = """{"success":false,"code":"VALIDATION_FAILED","message":"Email đã tồn tại"}"""
        val client = createMockClient(HttpStatusCode.UnprocessableEntity, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.register("test@example.com", "Password@123", "Nguyễn Văn A")

        assertTrue(result.isFailure)
    }

    @Test
    fun login_Success_SavesSessionAndReturnsAuthSession() = runTest {
        val json = """{"success":true,"data":{"access_token":"access_123","refresh_token":"refresh_123","user_id":"user_abc","email":"test@example.com","expires_in":3600}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.login("test@example.com", "Password@123")

        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("access_123", session?.accessToken)
        assertEquals("refresh_123", session?.refreshToken)
        assertEquals("user_abc", session?.userId)
        assertEquals("test@example.com", session?.email)
        assertEquals("access_123", tokenManager.getAccessToken())
        assertEquals("refresh_123", tokenManager.getRefreshToken())
        assertEquals("user_abc", tokenManager.getUserId())
    }

    @Test
    fun login_Failure_ReturnsException() = runTest {
        val json = """{"success":false,"code":"UNAUTHORIZED","message":"Email hoặc mật khẩu không đúng"}"""
        val client = createMockClient(HttpStatusCode.Unauthorized, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.login("test@example.com", "WrongPassword")

        assertTrue(result.isFailure)
        assertNull(tokenManager.getAccessToken())
    }

    @Test
    fun verifyOtp_Success_SavesSessionAndReturnsAuthSession() = runTest {
        val json = """{"success":true,"data":{"access_token":"otp_access_token","refresh_token":"otp_refresh_token","user_id":"user_otp","email":"otp@example.com","expires_in":3600}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.verifyOtp("otp@example.com", "123456", "signup")

        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertEquals("otp_access_token", session?.accessToken)
        assertEquals("otp_access_token", tokenManager.getAccessToken())
    }

    @Test
    fun verifyOtp_Failure_ReturnsException() = runTest {
        val json = """{"success":false,"code":"VALIDATION_FAILED","message":"OTP không hợp lệ hoặc đã hết hạn"}"""
        val client = createMockClient(HttpStatusCode.UnprocessableEntity, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.verifyOtp("otp@example.com", "000000", "signup")

        assertTrue(result.isFailure)
    }

    @Test
    fun refreshToken_Success_SavesNewSession() = runTest {
        val json = """{"success":true,"data":{"access_token":"new_access","refresh_token":"new_refresh","user_id":"user_abc","email":"test@example.com","expires_in":3600}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.refreshToken("old_refresh")

        assertTrue(result.isSuccess)
        assertEquals("new_access", tokenManager.getAccessToken())
        assertEquals("new_refresh", tokenManager.getRefreshToken())
    }

    @Test
    fun refreshToken_Failure_ReturnsException() = runTest {
        val json = """{"success":false,"code":"UNAUTHORIZED","message":"Refresh token không hợp lệ"}"""
        val client = createMockClient(HttpStatusCode.Unauthorized, json)
        val tokenManager = TokenManager(null)
        val repo = AuthRepositoryImpl(AuthRemoteDataSource(client), tokenManager)

        val result = repo.refreshToken("invalid_refresh")

        assertTrue(result.isFailure)
    }
}
