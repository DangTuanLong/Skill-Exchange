package com.skillexchange.app.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton phát sự kiện hết phiên đăng nhập (401 + refresh thất bại).
 *
 * - Emitter: [provideHttpClient] (trong NetworkModule) gọi [notifySessionExpired]
 *   sau khi clearSession().
 * - Collector: [SkillExchangeNavHost] quan sát và điều hướng về Login khi nhận event.
 *
 * Dùng extraBufferCapacity = 1 để không block khi chưa có collector (tryEmit không suspend).
 * replay = 0: collector mới không nhận sự kiện cũ (tránh navigate lại sau config change).
 */
object SessionManager {
    private val _sessionExpiredFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        replay = 0
    )
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow.asSharedFlow()

    /** Phát sự kiện session hết hạn. Thread-safe, không suspend. */
    fun notifySessionExpired() {
        _sessionExpiredFlow.tryEmit(Unit)
    }
}
