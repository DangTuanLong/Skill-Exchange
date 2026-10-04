package com.skillexchange.app.core.network

import android.content.Context
import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.auth.ApiWrapper
import com.skillexchange.app.data.remote.auth.AuthSessionDto
import com.skillexchange.app.data.remote.auth.RefreshRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

object NetworkModule {
    val module = module {
        single { provideHttpClient(androidContext(), get()) }
    }
}

/**
 * Ktor HttpClient với:
 * - JSON content negotiation
 * - Bearer Auth plugin: gắn access token tự động; nhận 401 → gọi /api/auth/refresh 1 lần →
 *   retry; nếu refresh thất bại → clearSession() + [SessionManager.notifySessionExpired].
 * - Auth endpoints (/api/auth/) bỏ qua bước gắn token ban đầu để tránh vòng lặp.
 * - Concurrent 401: Ktor Auth plugin tự đảm bảo chỉ 1 refresh request được gửi.
 *
 * Token KHÔNG được log (chuẩn §8 AGENTS.md).
 */
fun provideHttpClient(context: Context, tokenManager: TokenManager): HttpClient {
    return HttpClient(Android) {
        engine {
            connectTimeout = Constants.CONNECT_TIMEOUT_MS.toInt()
            socketTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        // Log body nhưng KHÔNG log Authorization header (giá trị token)
        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) {
                    // Lọc dòng có "Authorization:" để không log token
                    if (!message.startsWith("Authorization:")) {
                        android.util.Log.d("KtorClient", message)
                    }
                }
            }
        }

        // Ktor Auth plugin với bearer provider — xử lý 401 + auto-refresh
        install(Auth) {
            bearer {
                // Cung cấp token hiện tại cho mỗi request được bảo vệ
                loadTokens {
                    val access = tokenManager.getAccessToken() ?: return@loadTokens null
                    val refresh = tokenManager.getRefreshToken() ?: return@loadTokens null
                    BearerTokens(accessToken = access, refreshToken = refresh)
                }

                // Gọi khi nhận 401. Ktor đảm bảo block này chỉ chạy 1 lần dù
                // có nhiều request cùng nhận 401 đồng thời.
                refreshTokens {
                    val oldRefresh = tokenManager.getRefreshToken()
                    if (oldRefresh == null) {
                        tokenManager.clearSession()          // giữ onboardingSeen (FR-AUTH-6)
                        SessionManager.notifySessionExpired()
                        return@refreshTokens null
                    }
                    try {
                        // markAsRefreshTokenRequest() đảm bảo request này không kích hoạt
                        // refresh thêm lần nữa nếu bản thân nó nhận 401.
                        val response = client.post("${Constants.BASE_URL}/api/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequestDto(oldRefresh))
                        }.body<ApiWrapper<AuthSessionDto>>()

                        val session = response.data
                        if (session == null) {
                            tokenManager.clearSession()
                            SessionManager.notifySessionExpired()
                            return@refreshTokens null
                        }
                        tokenManager.saveSession(
                            accessToken  = session.accessToken,
                            refreshToken = session.refreshToken,
                            userId       = session.userId
                        )
                        BearerTokens(
                            accessToken  = session.accessToken,
                            refreshToken = session.refreshToken
                        )
                    } catch (_: Exception) {
                        // Mạng lỗi hoặc server lỗi — xóa session, điều hướng về Login
                        tokenManager.clearSession()
                        SessionManager.notifySessionExpired()
                        null
                    }
                }

                // Không gắn token (và không trigger refresh) cho auth endpoints.
                // Điều này ngăn vòng lặp: refresh call → 401 → refresh call → ...
                sendWithoutRequest { request ->
                    !request.url.toString().contains("/api/auth/")
                }
            }
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
            url(Constants.BASE_URL)
            // Bearer token được Auth plugin gắn tự động — không cần header thủ công
        }
    }
}
