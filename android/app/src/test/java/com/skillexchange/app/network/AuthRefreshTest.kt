package com.skillexchange.app.network

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.SessionManager
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.auth.ApiWrapper
import com.skillexchange.app.data.remote.auth.AuthSessionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Fake TokenManager lưu in-memory, không cần Context. */
private class FakeTokenManager : TokenManager(context = null) {
    var mockAccessToken: String? = "old-access"
    var mockRefreshToken: String? = "old-refresh"
    var sessionCleared = false
    var onboardingSeen = false

    override fun getAccessToken() = mockAccessToken
    override fun getRefreshToken() = mockRefreshToken
    override fun clearSession() { sessionCleared = true; mockAccessToken = null; mockRefreshToken = null }
    override fun isOnboardingSeen() = onboardingSeen
    override fun saveSession(accessToken: String, refreshToken: String, userId: String) {
        this.mockAccessToken = accessToken; this.mockRefreshToken = refreshToken
    }
}

/** Xây dựng HttpClient dùng MockEngine, sao chép cấu hình Auth từ provideHttpClient(). */
private fun buildMockClient(
    tokenManager: FakeTokenManager,
    handler: MockRequestHandler
): HttpClient {
    return HttpClient(MockEngine) {
        engine { addHandler(handler) }

        install(ContentNegotiation) {
            json(json)
        }

        install(Auth) {
            bearer {
                loadTokens {
                    val a = tokenManager.getAccessToken() ?: return@loadTokens null
                    val r = tokenManager.getRefreshToken() ?: return@loadTokens null
                    BearerTokens(accessToken = a, refreshToken = r)
                }
                refreshTokens {
                    val oldRefresh = tokenManager.getRefreshToken()
                    if (oldRefresh == null) {
                        tokenManager.clearSession()
                        SessionManager.notifySessionExpired()
                        return@refreshTokens null
                    }
                    try {
                        val response = client.post("${Constants.BASE_URL}/api/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(mapOf("refresh_token" to oldRefresh))
                        }.body<ApiWrapper<AuthSessionDto>>()
                        val session = response.data
                        if (session == null) {
                            tokenManager.clearSession()
                            SessionManager.notifySessionExpired()
                            return@refreshTokens null
                        }
                        tokenManager.saveSession(session.accessToken, session.refreshToken, session.userId)
                        BearerTokens(session.accessToken, session.refreshToken)
                    } catch (_: Exception) {
                        tokenManager.clearSession()
                        SessionManager.notifySessionExpired()
                        null
                    }
                }
                sendWithoutRequest { request ->
                    !request.url.toString().contains("/api/auth/")
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── 1. 401 → refresh thành công → retry trả về 200 ────────────────────
    @Test
    fun `401 triggers refresh and retry succeeds`() = runTest {
        val tokenManager = FakeTokenManager()
        val requestCount = AtomicInteger(0)

        val client = buildMockClient(tokenManager) { request ->
            val path = request.url.encodedPath
            when {
                // Refresh endpoint: trả token mới
                path.contains("/api/auth/refresh") -> {
                    respond(
                        content = json.encodeToString(
                            ApiWrapper(success = true, data = AuthSessionDto(
                                accessToken  = "new-access",
                                refreshToken = "new-refresh",
                                userId       = "user-1",
                                email        = "test@test.com"
                            ))
                        ),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                // Protected endpoint: lần 1 → 401; lần 2 (sau refresh) → 200
                path.contains("/api/protected") -> {
                    if (requestCount.getAndIncrement() == 0) {
                        respond("Unauthorized", HttpStatusCode.Unauthorized)
                    } else {
                        respond(
                            content = """{"ok":true}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
                else -> respond("Not found", HttpStatusCode.NotFound)
            }
        }

        val response = client.get("${Constants.BASE_URL}/api/protected")
        advanceUntilIdle()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("new-access", tokenManager.mockAccessToken)
        assertEquals("new-refresh", tokenManager.mockRefreshToken)
    }

    // ─── 2. Refresh thất bại → clearSession() + SessionExpired ─────────────
    @Test
    fun `refresh failure clears session and emits SessionExpired`() = runTest {
        val tokenManager = FakeTokenManager()

        val client = buildMockClient(tokenManager) { request ->
            // Cả protected và refresh đều trả 401
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }

        // Subscribe SessionExpired trước khi trigger
        val sessionExpiredJob = async {
            SessionManager.sessionExpiredFlow.first()
        }

        // Gọi protected → 401 → refresh → 401 → clearSession
        runCatching { client.get("${Constants.BASE_URL}/api/protected") }
        advanceUntilIdle()

        assertTrue("clearSession() phải được gọi", tokenManager.sessionCleared)
        assertNull("accessToken phải null", tokenManager.mockAccessToken)
        // sessionExpiredFlow phải emit
        assertNotNull(sessionExpiredJob.await())
    }

    // ─── 3. Refresh failure không xóa onboardingSeen ────────────────────────
    @Test
    fun `refresh failure preserves onboardingSeen flag`() = runTest {
        val tokenManager = FakeTokenManager().apply { onboardingSeen = true }

        val client = buildMockClient(tokenManager) {
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }

        runCatching { client.get("${Constants.BASE_URL}/api/protected") }
        advanceUntilIdle()

        assertTrue("clearSession() gọi nhưng onboardingSeen vẫn true", tokenManager.onboardingSeen)
    }

    // ─── 4. Auth endpoints không bị intercept (không vòng lặp) ─────────────
    @Test
    fun `auth endpoints are not intercepted by bearer`() = runTest {
        val tokenManager = FakeTokenManager()
        val refreshCallCount = AtomicInteger(0)

        val client = buildMockClient(tokenManager) { request ->
            val path = request.url.encodedPath
            if (path.contains("/api/auth/refresh")) refreshCallCount.incrementAndGet()
            // Auth endpoint trả 200 bình thường
            respond("""{"ok":true}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        // Login không trigger refresh
        client.post("${Constants.BASE_URL}/api/auth/login") { setBody("{}") }
        advanceUntilIdle()

        assertEquals("Auth endpoints không được kích hoạt refresh", 0, refreshCallCount.get())
    }
}
