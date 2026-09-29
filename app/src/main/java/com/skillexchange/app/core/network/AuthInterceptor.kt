package com.skillexchange.app.core.network

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import com.skillexchange.app.data.remote.auth.RefreshRequestDto
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.plugins.auth.providers.bearer

/**
 * Cấu hình Bearer Auth + Auto-refresh token cho Ktor Client.
 * Gọi hàm này bên trong block `install(Auth) { ... }` của HttpClient.
 *
 * Khi server trả 401, tự động dùng refresh_token để lấy access_token mới.
 */
suspend fun refreshBearerTokens(
    params: RefreshTokensParams,
    tokenManager: TokenManager,
    authRemoteDataSource: AuthRemoteDataSource
): BearerTokens? {
    val oldRefresh = tokenManager.getRefreshToken() ?: return null
    return try {
        val resp = authRemoteDataSource.refresh(RefreshRequestDto(oldRefresh))
        val data = resp.data ?: run {
            tokenManager.clearAll()
            return null
        }
        tokenManager.saveSession(data.accessToken, data.refreshToken, data.userId)
        BearerTokens(accessToken = data.accessToken, refreshToken = data.refreshToken)
    } catch (e: Exception) {
        tokenManager.clearAll()
        null
    }
}
