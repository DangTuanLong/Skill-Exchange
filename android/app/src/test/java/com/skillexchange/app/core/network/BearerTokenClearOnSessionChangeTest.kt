package com.skillexchange.app.core.network

import com.skillexchange.app.core.security.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BearerTokenClearOnSessionChangeTest {

    @Test
    fun sessionChange_clearsCachedBearerTokens_andSendsNewUserToken() = runBlocking {
        val tokenManager = TokenManager(null)
        val capturedAuthHeaders = mutableListOf<String?>()

        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization])
            respond("OK", HttpStatusCode.OK)
        }

        val client = HttpClient(mockEngine) {
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = tokenManager.getAccessToken() ?: return@loadTokens null
                        val refresh = tokenManager.getRefreshToken() ?: return@loadTokens null
                        BearerTokens(accessToken = access, refreshToken = refresh)
                    }
                }
            }
        }.also { c ->
            tokenManager.setOnSessionChangedListener {
                c.authProviders.filterIsInstance<BearerAuthProvider>().forEach {
                    it.clearToken()
                }
            }
        }

        // 1. Đăng nhập tài khoản 1
        tokenManager.saveSession("token_user_1", "refresh_1", "user_1")
        client.get("https://example.com/api/profile/me")
        assertEquals(1, capturedAuthHeaders.size)
        assertEquals("Bearer token_user_1", capturedAuthHeaders[0])

        // 2. Chuyển sang tài khoản 2 mà không khởi động lại client
        tokenManager.saveSession("token_user_2", "refresh_2", "user_2")
        client.get("https://example.com/api/profile/me")
        assertEquals(2, capturedAuthHeaders.size)
        assertEquals("Bearer token_user_2", capturedAuthHeaders[1])

        // 3. Đăng xuất tài khoản
        tokenManager.clearSession()
        client.get("https://example.com/api/public")
        assertEquals(3, capturedAuthHeaders.size)
        // Khi không có session, không gửi header Bearer
        assertEquals(null, capturedAuthHeaders[2])
    }
}
